package com.moara.moa.consumable;

import com.moara.moa.group.AccessGroupService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 주문 주기 알림. <b>"A4용지 주문 주기가 돌아왔습니다. 현재 재고를 확인해주세요."</b>
 *
 * <p>이건 발주 승인이 아니라 <b>리마인더</b>다. 시스템은 재고를 모르고, 얼마나 살지도 정하지
 * 않는다 — 확인은 사람이 한다. 그래서 틀려도 비용이 창고 한 번 보는 것이고, 표본이 들쭉날쭉해도
 * 침묵하지 않고 <b>확신의 정도를 문구에 반영해</b> 보낸다. 근거 없는 숫자를 확신에 차서
 * 통지하는 것이 이 기능을 죽이는 가장 빠른 길이다.
 *
 * <h2>중복 방지</h2>
 * {@link NotificationService#notifyOnce}의 제목 기준 하루 1회 규칙을 그대로 쓴다. 제목에
 * <b>마지막 주문일</b>을 넣어, 새 주문이 기록되면 제목이 달라져 다음 주기에 다시 알림이 간다
 * (별도 해제 로직이 필요 없다).
 */
@Service
public class ConsumableAlertService {
  /** 만료 알림과 같은 기준 시간대를 쓴다 — 두 배치가 다른 '오늘'을 보면 설명할 수 없다. */
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  private static final Set<UserRole> FALLBACK_ROLES =
      Set.of(UserRole.ASSET_MANAGER, UserRole.TENANT_ADMIN);

  private final TenantService tenantService;
  private final ConsumableService consumableService;
  private final ManagedUserService userService;
  private final AccessGroupService groupService;
  private final NotificationService notificationService;
  private final NotificationMailer mailer;

  public ConsumableAlertService(
      TenantService tenantService, ConsumableService consumableService,
      ManagedUserService userService, AccessGroupService groupService,
      NotificationService notificationService, NotificationMailer mailer) {
    this.tenantService = tenantService;
    this.consumableService = consumableService;
    this.userService = userService;
    this.groupService = groupService;
    this.notificationService = notificationService;
    this.mailer = mailer;
  }

  public int notifyAllTenants() {
    int sent = 0;
    for (Tenant tenant : tenantService.findAll()) {
      sent += notifyTenant(tenant.getId());
    }
    return sent;
  }

  /**
   * 한 기관의 주문 시점이 된 품목을 알린다.
   *
   * @return 새로 만든 인앱 알림 건수(메일 통수는 포함하지 않는다 — 기관이 SMTP를 안 걸었으면
   *     0이라, 이 값이 흔들리면 배치 성공 여부를 판단할 수 없다)
   */
  public int notifyTenant(UUID tenantId) {
    LocalDate today = LocalDate.now(ZONE);
    Map<UUID, ConsumableForecast> forecasts = consumableService.forecasts(tenantId, today);
    List<ConsumableItem> due = consumableService.findActive(tenantId).stream()
        .filter(item -> forecasts.get(item.getId()) != null
            && forecasts.get(item.getId()).isDue(today))
        .toList();
    if (due.isEmpty()) {
      return 0;
    }
    OffsetDateTime since = today.atStartOfDay(ZONE).toOffsetDateTime();
    Map<ManagedUser, List<String>> digest = new LinkedHashMap<>();
    int sent = 0;
    for (ConsumableItem item : due) {
      ConsumableForecast forecast = forecasts.get(item.getId());
      String title = title(item, forecast);
      String body = body(item, forecast, today);
      for (ManagedUser recipient : recipients(tenantId, item)) {
        if (notificationService.notifyOnce(
            tenantId, recipient.getId(), title, body, "/consumables/" + item.getId(), since)) {
          sent++;
          digest.computeIfAbsent(recipient, key -> new ArrayList<>()).add(title + "\n  " + body);
        }
      }
    }
    // 품목이 여러 개여도 사람당 한 통. 묶는 규칙은 NotificationMailer 공용이다.
    mailer.sendDigest(tenantId, emailLines(digest), "[MOA] 소모품 주문 시점 %d건",
        "아래 소모품의 주문 주기가 돌아왔습니다. 재고를 확인해 주세요.", "\n\n",
        "/consumables");
    return sent;
  }

  /** 제목에 마지막 주문일을 넣어 같은 주기에 한 번만 가게 한다(새 주문이 오면 제목이 바뀐다). */
  private String title(ConsumableItem item, ConsumableForecast forecast) {
    String stamp = forecast.lastOrderedOn() == null ? "첫 주문" : forecast.lastOrderedOn().toString();
    return "[소모품] " + item.name() + " 주문 주기가 돌아왔습니다 (" + stamp + " 기준)";
  }

  /**
   * 근거 수치를 그대로 싣는다. 담당자가 "이번엔 아직 남았는데"라고 판단할 수 있어야 하고,
   * 계산이 틀렸다면 그것도 바로 보여야 한다.
   */
  private String body(ConsumableItem item, ConsumableForecast forecast, LocalDate today) {
    StringBuilder body = new StringBuilder("현재 재고를 확인해주세요.");
    switch (forecast.basis()) {
      case IRREGULAR -> body.append("\n주문 간격이 일정하지 않지만");
      case CONFIGURED -> body.append("\n설정하신 주기 기준이며 실측 근거는 아직 없습니다.");
      default -> body.append("\n최근 주문 간격 기준");
    }
    if (forecast.lastOrderedOn() != null) {
      body.append(" 마지막 주문 ").append(forecast.lastOrderedOn());
      if (forecast.lastQuantity() != null) {
        body.append(" · ").append(forecast.lastQuantity())
            .append(item.unitOrDefault());
      }
      body.append(" 이후 ")
          .append(java.time.temporal.ChronoUnit.DAYS.between(forecast.lastOrderedOn(), today))
          .append("일 지났습니다.");
    }
    if (!forecast.intervals().isEmpty()) {
      body.append("\n최근 간격: ")
          .append(String.join(" · ", forecast.intervals().stream().map(String::valueOf).toList()))
          .append("일");
    }
    if (forecast.cycleDays() != null) {
      body.append("\n추정 주기: ").append(forecast.cycleDays()).append("일");
    }
    return body.toString();
  }

  /** 담당팀 리더 + 자산 관리자. 담당팀이 없으면 자산 관리자·기관 관리자에게 간다. */
  private List<ManagedUser> recipients(UUID tenantId, ConsumableItem item) {
    Set<ManagedUser> out = new LinkedHashSet<>();
    List<ManagedUser> active = userService.findByTenant(tenantId).stream()
        .filter(u -> u.getStatus() == UserStatus.ACTIVE)
        .toList();
    if (item.getOwnerGroupId() != null) {
      Set<UUID> leaders = groupService.leaderIds(tenantId, item.getOwnerGroupId());
      active.stream().filter(u -> leaders.contains(u.getId())).forEach(out::add);
    }
    active.stream()
        .filter(u -> FALLBACK_ROLES.stream().anyMatch(u::hasRole))
        .forEach(out::add);
    return new ArrayList<>(out);
  }

  /** 수신자를 이메일 키로 바꾼다(메일 발송기는 사용자 개념을 모른다). */
  private Map<String, List<String>> emailLines(Map<ManagedUser, List<String>> digest) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    digest.forEach((user, lines) -> out.put(user.getEmail(), lines));
    return out;
  }
}
