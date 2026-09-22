package com.moara.moa.invitation;

import com.moara.moa.group.AccessGroupService;
import com.moara.moa.mail.MailSetting;
import com.moara.moa.mail.MailSettingService;
import com.moara.moa.mail.MailService;
import com.moara.moa.onboarding.OnboardingService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 기반 온보딩. 관리자/부서장이 이메일(다중)로 초대하면 부서·역할·온보딩 템플릿이 미리 지정된 초대가
 * 만들어지고, 수락 링크로 사용자가 비밀번호·연락처만 입력하면 계정이 생성되며 지정 부서·역할·온보딩이 적용된다.
 * 토큰은 해시로만 저장한다(원문 미보관). 수락 링크 클릭이 곧 이메일 소유 증명이자 관리자 승인의 근거다.
 */
@Service
@Transactional(readOnly = true)
public class InvitationService {
  private static final int EXPIRY_DAYS = 7;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserInvitationRepository repository;
  private final ManagedUserService userService;
  private final AccessGroupService groupService;
  private final OnboardingService onboardingService;
  private final MailSettingService mailSettingService;
  private final MailService mailService;

  public InvitationService(
      UserInvitationRepository repository, ManagedUserService userService,
      AccessGroupService groupService, OnboardingService onboardingService,
      MailSettingService mailSettingService, MailService mailService) {
    this.repository = repository;
    this.userService = userService;
    this.groupService = groupService;
    this.onboardingService = onboardingService;
    this.mailSettingService = mailSettingService;
    this.mailService = mailService;
  }

  /** 초대 1건 결과(화면 표시/링크 복사용). skipped면 reason에 사유, 아니면 link·emailed. */
  public record InviteResult(String email, String link, boolean emailed, boolean skipped, String reason) {}

  public List<UserInvitation> list(UUID tenantId) {
    return repository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
  }

  /**
   * 다중 초대. 이미 계정이 있거나 대기 중 초대가 있으면 건너뛴다. 기관 SMTP가 설정돼 있으면 링크를 메일로
   * 보내고, 아니면 화면에서 복사할 수 있도록 링크를 돌려준다.
   */
  @Transactional
  public List<InviteResult> invite(
      UUID tenantId, UUID invitedBy, List<String> emails, String name, UUID groupId,
      Set<UserRole> roles, UUID templateId, String baseUrl) {
    OffsetDateTime now = OffsetDateTime.now();
    Optional<MailSetting> mail = mailSettingService.findForTenant(tenantId)
        .filter(MailSetting::isEnabled);
    String rolesCsv = toCsv(roles);
    List<InviteResult> results = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String raw : emails) {
      String email = raw == null ? "" : raw.trim().toLowerCase();
      if (email.isEmpty() || !email.contains("@") || !seen.add(email)) {
        continue; // 빈 값·형식 이상·중복(같은 요청 내) 무시
      }
      if (emailAlreadyUser(tenantId, email)) {
        results.add(new InviteResult(email, null, false, true, "이미 계정이 있는 이메일"));
        continue;
      }
      if (repository.existsByTenantIdAndEmailIgnoreCaseAndStatus(tenantId, email, InvitationStatus.PENDING)) {
        results.add(new InviteResult(email, null, false, true, "이미 대기 중인 초대"));
        continue;
      }
      String token = newToken();
      repository.save(new UserInvitation(
          UUID.randomUUID(), tenantId, email, name, groupId, rolesCsv, templateId,
          sha256(token), invitedBy, now, now.plusDays(EXPIRY_DAYS)));
      String link = baseUrl + "/invite/" + token;
      boolean emailed = mail.isPresent() && trySendInvite(mail.get(), email, link);
      results.add(new InviteResult(email, link, emailed, false, null));
    }
    return results;
  }

  /** 토큰으로 수락 가능한 초대를 찾는다(대기·미만료). 없으면 예외. */
  public UserInvitation acceptable(String token) {
    return repository.findByTokenHash(sha256(token))
        .filter(inv -> inv.isAcceptable(OffsetDateTime.now()))
        .orElseThrow(InvitationInvalidException::new);
  }

  /**
   * 초대 수락 → 계정 생성(아이디=이메일, 활성) + 지정 부서 배정 + 온보딩 적용. 초대는 ACCEPTED로 마감.
   */
  @Transactional
  public ManagedUser accept(String token, String name, String phone, String rawPassword) {
    UserInvitation inv = acceptable(token);
    String finalName = (name == null || name.isBlank())
        ? (inv.getName() == null ? inv.getEmail() : inv.getName()) : name.trim();
    // username = 표시 이름(핸들). 로그인 키는 이메일이다(username은 이메일 아님).
    UserForm form = new UserForm(
        finalName, finalName, inv.getEmail(), phone, rawPassword, UserStatus.ACTIVE);
    ManagedUser user = userService.create(inv.getTenantId(), form, parseRoles(inv.getRoles()));
    if (inv.getGroupId() != null) {
      trySilently(() -> groupService.addMember(inv.getTenantId(), inv.getGroupId(), user.getId()));
    }
    if (inv.getOnboardingTemplateId() != null) {
      trySilently(() -> onboardingService.apply(inv.getTenantId(), user.getId(), inv.getOnboardingTemplateId()));
    }
    inv.accept(OffsetDateTime.now());
    return user;
  }

  @Transactional
  public void revoke(UUID tenantId, UUID id) {
    repository.findByTenantIdAndId(tenantId, id).ifPresent(UserInvitation::revoke);
  }

  /**
   * 재발송: 새 토큰·만료로 갱신하고(다시 PENDING), 기관 메일이 있으면 재발송, 없으면 새 링크를 돌려준다.
   * 링크(원문 토큰)는 저장하지 않으므로, 링크를 잃어버렸을 때 이 방법으로 다시 얻는다.
   */
  @Transactional
  public InviteResult resend(UUID tenantId, UUID id, String baseUrl) {
    UserInvitation inv = repository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(InvitationInvalidException::new);
    if (inv.getStatus() == InvitationStatus.ACCEPTED) {
      return new InviteResult(inv.getEmail(), null, false, true, "이미 수락된 초대");
    }
    OffsetDateTime now = OffsetDateTime.now();
    String token = newToken();
    inv.reissue(sha256(token), now.plusDays(EXPIRY_DAYS));
    String link = baseUrl + "/invite/" + token;
    boolean emailed = mailSettingService.findForTenant(tenantId).filter(MailSetting::isEnabled)
        .map(setting -> trySendInvite(setting, inv.getEmail(), link)).orElse(false);
    return new InviteResult(inv.getEmail(), link, emailed, false, null);
  }

  // --- 내부 ---

  private boolean emailAlreadyUser(UUID tenantId, String email) {
    return userService.findByTenant(tenantId).stream()
        .anyMatch(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(email));
  }

  private boolean trySendInvite(MailSetting setting, String email, String link) {
    String subject = "[MOA] 계정 초대";
    String body = "안녕하세요.\n아래 링크로 접속해 비밀번호를 설정하면 계정 등록이 완료됩니다.\n\n"
        + link + "\n\n이 링크는 " + EXPIRY_DAYS + "일간 유효합니다.";
    try {
      return mailService.sendBulk(setting, List.of(email), subject, body).sent() > 0;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private Set<UserRole> parseRoles(String csv) {
    Set<UserRole> roles = new HashSet<>();
    if (csv != null) {
      for (String part : csv.split(",")) {
        String r = part.trim();
        if (!r.isEmpty()) {
          try {
            roles.add(UserRole.valueOf(r));
          } catch (IllegalArgumentException ignored) {
            // 알 수 없는 역할은 무시(부여 정제는 ManagedUserService가 담당).
          }
        }
      }
    }
    if (roles.isEmpty()) {
      roles.add(UserRole.USER);
    }
    return roles;
  }

  private String toCsv(Set<UserRole> roles) {
    if (roles == null || roles.isEmpty()) {
      return "USER";
    }
    return roles.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("USER");
  }

  private String newToken() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String sha256(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : digest) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private void trySilently(Runnable action) {
    try {
      action.run();
    } catch (RuntimeException ignored) {
      // 부서 배정/온보딩 실패가 계정 생성을 되돌리지 않는다(부분 성공 허용).
    }
  }
}
