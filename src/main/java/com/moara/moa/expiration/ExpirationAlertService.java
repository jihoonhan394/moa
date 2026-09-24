package com.moara.moa.expiration;

import com.moara.moa.notification.NotificationMailer;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 만료 임박 항목을 감지해 기관의 관리 권한자에게 <b>인앱 알림 + 메일</b>을 보낸다.
 *
 * <p>화면(인벤토리 등록/목록, 트래커 패널)이 "임박 시 알림이 갑니다"라고 안내하는데 실제 발송이
 * 없었다 — 이 서비스가 그 약속을 이행한다. 스케줄러와 분리해 단독 테스트가 가능하다.
 *
 * <p>임계일(D-14/7/3/1/0)에 <b>정확히 일치</b>할 때만 보내므로 매일 쌓이지 않고 항목당 최대 5회다.
 * 이미 만료된 항목은 화면에 "만료 N일 지남"으로 보이므로 알림을 내지 않는다(소음 억제).
 *
 * <h2>메일은 사람당 한 통으로 묶는다</h2>
 * 임박 항목이 10건이면 인앱 알림은 10건이 맞지만 메일까지 10통이면 수신자가 메일을 차단한다.
 * 이번 실행에서 <b>새로 알린 것만</b> 모아 수신자별 한 통으로 보낸다.
 *
 * <h2>트랜잭션을 걸지 않는다</h2>
 * 쓰기는 {@link NotificationService#notifyOnce}가 건별로 처리하고 그 안에서 중복 검사와 삽입이
 * 원자적이다. 바깥을 하나의 트랜잭션으로 묶으면 (1) SMTP 지연이 DB 커넥션을 붙잡고
 * (2) 뒤쪽 기관에서 실패했을 때 앞쪽 기관 알림까지 되돌아간다. 배치는 부분 진행이 남는 편이 낫다.
 */
@Service
public class ExpirationAlertService {
  /** 배치 기준 시간대. 화면·D-day 계산과 같은 기준을 쓴다. */
  public static final ZoneId ZONE = ExpirationService.ZONE;

  /** 알림을 보낼 잔여일. 이 값과 정확히 같을 때만 발송한다. */
  private static final Set<Long> ALERT_DAYS = Set.of(14L, 7L, 3L, 1L, 0L);

  /** 만료 대시보드를 볼 수 있는 역할 = 알림 수신 대상(SecurityConfig의 /expirations 규칙과 일치). */
  private static final Set<UserRole> RECIPIENT_ROLES =
      Set.of(UserRole.TENANT_ADMIN, UserRole.INFRA_MANAGER, UserRole.ASSET_MANAGER);

  private final TenantService tenantService;
  private final ExpirationService expirationService;
  private final ManagedUserService userService;
  private final NotificationService notificationService;
  private final NotificationMailer mailer;

  public ExpirationAlertService(
      TenantService tenantService, ExpirationService expirationService,
      ManagedUserService userService, NotificationService notificationService,
      NotificationMailer mailer) {
    this.tenantService = tenantService;
    this.expirationService = expirationService;
    this.userService = userService;
    this.notificationService = notificationService;
    this.mailer = mailer;
  }

  /** 전 기관을 순회하며 알림을 보낸다. 반환값은 실제 발송 건수(중복 차단된 것은 제외). */
  public int notifyAllTenants() {
    int sent = 0;
    for (Tenant tenant : tenantService.findAll()) {
      sent += notifyTenant(tenant.getId());
    }
    return sent;
  }

  /**
   * 한 기관의 임박 항목을 알린다. 조회·발송 모두 이 기관으로 스코프되며, 수신자도 이 기관
   * 사용자만이다(전역 역할 조회를 쓰지 않는다 — 교차 테넌트 발송 방지).
   *
   * @return 새로 만든 <b>인앱 알림</b> 건수. 메일 통수는 포함하지 않는다(메일은 보조 경로이고
   *     기관이 SMTP를 안 걸었으면 0이므로, 이 값이 흔들리면 배치 성공 여부를 판단할 수 없다).
   */
  public int notifyTenant(UUID tenantId) {
    List<ExpirationRow> due = expirationService.findAll(tenantId).stream()
        .filter(row -> ALERT_DAYS.contains(row.daysLeft()))
        .toList();
    if (due.isEmpty()) {
      return 0;
    }
    List<ManagedUser> recipients = recipients(tenantId);
    if (recipients.isEmpty()) {
      return 0;
    }
    OffsetDateTime since = LocalDate.now(ZONE).atStartOfDay(ZONE).toOffsetDateTime();
    // 수신자별로 "이번에 새로 알린 것"만 모은다 — 메일 한 통에 담을 내용이다.
    Map<ManagedUser, List<String>> digest = new LinkedHashMap<>();
    int sent = 0;
    for (ExpirationRow row : due) {
      String title = title(row);
      String body = body(row);
      for (ManagedUser recipient : recipients) {
        if (notificationService.notifyOnce(
            tenantId, recipient.getId(), title, body, "/expirations", since)) {
          sent++;
          digest.computeIfAbsent(recipient, key -> new ArrayList<>()).add(title + " — " + body);
        }
      }
    }
    mailDigest(tenantId, digest);
    return sent;
  }

  /**
   * 수신자별 묶음 메일. 같은 목록을 받는 사람끼리는 한 번의 발송으로 묶어 SMTP 왕복을 줄인다
   * (보통 모든 관리자가 같은 항목을 받으므로 대개 한 덩어리가 된다).
   */
  private void mailDigest(UUID tenantId, Map<ManagedUser, List<String>> digest) {
    Map<String, List<String>> byBody = new LinkedHashMap<>();
    for (Map.Entry<ManagedUser, List<String>> entry : digest.entrySet()) {
      String email = entry.getKey().getEmail();
      if (email == null || email.isBlank()) {
        continue;
      }
      byBody.computeIfAbsent(String.join("\n", entry.getValue()), key -> new ArrayList<>())
          .add(email);
    }
    for (Map.Entry<String, List<String>> group : byBody.entrySet()) {
      int count = group.getKey().split("\n").length;
      mailer.send(
          tenantId, group.getValue(), "[MOA] 만료 임박 " + count + "건",
          "만료가 임박한 항목입니다.\n\n" + group.getKey(), "/expirations");
    }
  }

  /** 관리 권한을 가진 활성 사용자. 만료 대시보드를 볼 수 있는 사람에게만 알린다. */
  private List<ManagedUser> recipients(UUID tenantId) {
    return userService.findByTenant(tenantId).stream()
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .filter(user -> RECIPIENT_ROLES.stream().anyMatch(user::hasRole))
        .toList();
  }

  private String title(ExpirationRow row) {
    String when = row.daysLeft() == 0 ? "오늘 만료" : "D-" + row.daysLeft();
    return "[" + when + "] " + row.category() + " · " + row.label();
  }

  private String body(ExpirationRow row) {
    StringBuilder body = new StringBuilder();
    if (row.detail() != null && !row.detail().isBlank()) {
      body.append(row.detail()).append(" · ");
    }
    return body.append("만료일 ").append(row.expiresOn()).toString();
  }
}
