package com.moara.moa.expiration;

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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료 임박 항목을 감지해 기관의 관리 권한자에게 인앱 알림을 보낸다.
 *
 * <p>화면(인벤토리 등록/목록, 트래커 패널)이 "임박 시 알림이 갑니다"라고 안내하는데 실제 발송이
 * 없었다 — 이 서비스가 그 약속을 이행한다. 스케줄러와 분리해 단독 테스트가 가능하다.
 *
 * <p>임계일(D-14/7/3/1/0)에 <b>정확히 일치</b>할 때만 보내므로 매일 쌓이지 않고 항목당 최대 5회다.
 * 이미 만료된 항목은 화면에 "만료 N일 지남"으로 보이므로 알림을 내지 않는다(소음 억제).
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

  public ExpirationAlertService(
      TenantService tenantService, ExpirationService expirationService,
      ManagedUserService userService, NotificationService notificationService) {
    this.tenantService = tenantService;
    this.expirationService = expirationService;
    this.userService = userService;
    this.notificationService = notificationService;
  }

  /** 전 기관을 순회하며 알림을 보낸다. 반환값은 실제 발송 건수(중복 차단된 것은 제외). */
  @Transactional
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
   */
  @Transactional
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
    int sent = 0;
    for (ExpirationRow row : due) {
      String title = title(row);
      String body = body(row);
      for (ManagedUser recipient : recipients) {
        if (notificationService.notifyOnce(
            tenantId, recipient.getId(), title, body, "/expirations", since)) {
          sent++;
        }
      }
    }
    return sent;
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
