package com.moara.moa.maintenance;

import com.moara.moa.mail.MailService;
import com.moara.moa.mail.MailSetting;
import com.moara.moa.mail.MailSettingService;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.solution.SolutionAccessService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검/작업 일정 등록과 유지보수 담당자 관리. 점검창을 등록하면 관련자(담당자 + 솔루션 배정자)에게
 * 인앱 알림을 남기고, 기관 SMTP가 설정돼 있으면 메일도 함께 보낸다. 알림/메일 실패는 등록 자체를
 * 막지 않는다(관련자 통지는 부가 효과이지 트랜잭션 성패의 조건이 아니다).
 */
@Service
@Transactional(readOnly = true)
public class MaintenanceService {
  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final MaintenanceOwnerRepository ownerRepository;
  private final MaintenanceWindowRepository windowRepository;
  private final SolutionAccessService solutionAccessService;
  private final NotificationService notificationService;
  private final ManagedUserService userService;
  private final MailSettingService mailSettingService;
  private final MailService mailService;

  public MaintenanceService(
      MaintenanceOwnerRepository ownerRepository,
      MaintenanceWindowRepository windowRepository,
      SolutionAccessService solutionAccessService,
      NotificationService notificationService,
      ManagedUserService userService,
      MailSettingService mailSettingService,
      MailService mailService) {
    this.ownerRepository = ownerRepository;
    this.windowRepository = windowRepository;
    this.solutionAccessService = solutionAccessService;
    this.notificationService = notificationService;
    this.userService = userService;
    this.mailSettingService = mailSettingService;
    this.mailService = mailService;
  }

  // --- 담당자 ---

  public List<MaintenanceOwner> owners(UUID tenantId) {
    return ownerRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
  }

  /** 담당자 지정(멱등 — 같은 대상·사용자면 무시). */
  @Transactional
  public void addOwner(UUID tenantId, MaintenanceTargetType targetType, UUID targetId, UUID userId) {
    if (userId == null || targetType == null || targetId == null) {
      return;
    }
    if (!ownerRepository.existsByTenantIdAndTargetTypeAndTargetIdAndUserId(tenantId, targetType, targetId, userId)) {
      ownerRepository.save(new MaintenanceOwner(
          UUID.randomUUID(), tenantId, targetType, targetId, userId, OffsetDateTime.now()));
    }
  }

  @Transactional
  public void removeOwner(UUID tenantId, UUID ownerId) {
    ownerRepository.findByTenantIdAndId(tenantId, ownerId).ifPresent(ownerRepository::delete);
  }

  /** 퇴사 회수: 이 사용자를 모든 유지보수 담당에서 해제. 해제 건수 반환. */
  @Transactional
  public long removeOwnershipsOf(UUID tenantId, UUID userId) {
    return ownerRepository.deleteByTenantIdAndUserId(tenantId, userId);
  }

  // --- 점검창 ---

  public List<MaintenanceWindow> windows(UUID tenantId) {
    return windowRepository.findAllByTenantIdOrderByStartsAtDesc(tenantId);
  }

  /** 특정 대상(자산/솔루션)의 담당자. 운영 콘솔에서 재사용. */
  public List<MaintenanceOwner> ownersFor(UUID tenantId, MaintenanceTargetType targetType, UUID targetId) {
    return ownerRepository.findAllByTenantIdAndTargetTypeAndTargetId(tenantId, targetType, targetId);
  }

  /** 특정 대상의 점검·작업 이력(최신순). 운영 콘솔의 '작업 이력'으로 재사용. */
  public List<MaintenanceWindow> windowsFor(UUID tenantId, MaintenanceTargetType targetType, UUID targetId) {
    return windowRepository.findAllByTenantIdAndTargetTypeAndTargetIdOrderByStartsAtDesc(tenantId, targetType, targetId);
  }

  /**
   * 점검창을 등록하고 관련자에게 통지한다. 관련자 = 이 대상의 담당자 + (솔루션이면) 배정 사용자.
   * 등록자 본인은 통지 대상에서 제외한다(자기 알림 소음 방지).
   */
  @Transactional
  public MaintenanceWindow createWindow(
      UUID tenantId, MaintenanceTargetType targetType, UUID targetId, UUID actorUserId,
      MaintenanceWindowForm form) {
    MaintenanceWindow window = windowRepository.save(new MaintenanceWindow(
        UUID.randomUUID(), tenantId, targetType, targetId, actorUserId, form, OffsetDateTime.now()));
    notifyStakeholders(tenantId, targetType, targetId, actorUserId, window);
    return window;
  }

  /** 이 대상의 관련자 식별자(담당자 ∪ 솔루션 배정자). 등록자 본인은 제외. */
  private Set<UUID> stakeholders(UUID tenantId, MaintenanceTargetType targetType, UUID targetId, UUID actorUserId) {
    Set<UUID> ids = new LinkedHashSet<>();
    ownerRepository.findAllByTenantIdAndTargetTypeAndTargetId(tenantId, targetType, targetId)
        .forEach(owner -> ids.add(owner.getUserId()));
    if (targetType == MaintenanceTargetType.SOLUTION) {
      ids.addAll(solutionAccessService.assignedUserIds(tenantId, targetId));
    }
    ids.remove(actorUserId);
    return ids;
  }

  private void notifyStakeholders(
      UUID tenantId, MaintenanceTargetType targetType, UUID targetId, UUID actorUserId,
      MaintenanceWindow window) {
    Set<UUID> recipients = stakeholders(tenantId, targetType, targetId, actorUserId);
    if (recipients.isEmpty()) {
      return;
    }
    String title = "[점검] " + window.getTitle();
    String body = notificationBody(window);
    List<String> emails = new ArrayList<>();
    for (UUID userId : recipients) {
      notificationService.notify(tenantId, userId, title, body, "/maintenance");
      String email = emailOf(userId);
      if (email != null) {
        emails.add(email);
      }
    }
    sendMail(tenantId, emails, title, body);
  }

  private String notificationBody(MaintenanceWindow window) {
    StringBuilder sb = new StringBuilder();
    sb.append(window.getTargetType().getLabel()).append(" 점검 일정이 등록되었습니다.\n");
    if (window.getStartsAt() != null) {
      sb.append("시작: ").append(window.getStartsAt().format(WHEN)).append('\n');
    }
    if (window.getEndsAt() != null) {
      sb.append("종료: ").append(window.getEndsAt().format(WHEN)).append('\n');
    }
    if (window.getReason() != null) {
      sb.append("사유: ").append(window.getReason());
    }
    return sb.toString().trim();
  }

  private String emailOf(UUID userId) {
    try {
      ManagedUser user = userService.findById(userId);
      String email = user.getEmail();
      return email != null && !email.isBlank() ? email : null;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  /** 기관 SMTP가 설정·활성화돼 있을 때만 메일을 보낸다. 미설정/실패는 조용히 넘어간다(인앱 알림은 이미 남음). */
  private void sendMail(UUID tenantId, List<String> emails, String subject, String body) {
    if (emails.isEmpty()) {
      return;
    }
    Optional<MailSetting> setting = mailSettingService.findForTenant(tenantId);
    if (setting.isEmpty() || !setting.get().isEnabled()) {
      return;
    }
    try {
      mailService.sendBulk(setting.get(), emails, subject, body);
    } catch (RuntimeException exception) {
      // 메일 발송 실패는 점검 등록을 되돌리지 않는다(인앱 알림으로 최소 통지는 보장됨).
    }
  }
}
