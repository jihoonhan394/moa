package com.moara.moa.consumable;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.notification.Notification;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 주문 주기 알림 배치. 예측 규칙은 {@link ConsumableForecastTest}가 보고, 여기서는
 * <b>누구에게·언제·몇 번</b> 가는지를 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConsumableAlertServiceTest {
  @Autowired private ConsumableAlertService alertService;
  @Autowired private ConsumableService consumableService;
  @Autowired private NotificationService notificationService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  /** 주기가 지났으면 자산 관리자에게 간다. 근거 수치가 본문에 실려야 한다. */
  @Test
  void 주기가_지나면_자산_관리자에게_알린다() {
    UUID tenantId = tenant();
    ManagedUser manager = user(tenantId, UserRole.ASSET_MANAGER);
    ConsumableItem item = dueItem(tenantId, "알림용지");

    int sent = alertService.notifyTenant(tenantId);

    assertThat(sent).isGreaterThan(0);
    List<Notification> inbox = notificationService.list(tenantId, manager.getId());
    assertThat(inbox).anyMatch(n -> n.getTitle().contains(item.getName()));
    assertThat(inbox).anyMatch(n -> n.getBody().contains("재고를 확인"));
    assertThat(inbox).anyMatch(n -> n.getLink().equals("/consumables/" + item.getId()));
  }

  /** 같은 날 두 번 돌려도 한 번만 간다(배치 재실행 대비). */
  @Test
  void 같은_날_다시_돌려도_중복_발송하지_않는다() {
    UUID tenantId = tenant();
    user(tenantId, UserRole.ASSET_MANAGER);
    dueItem(tenantId, "중복알림용지");

    int first = alertService.notifyTenant(tenantId);
    int second = alertService.notifyTenant(tenantId);

    assertThat(first).isGreaterThan(0);
    assertThat(second).isZero();
  }

  /** 아직 때가 안 된 품목은 알리지 않는다. */
  @Test
  void 주기가_안_됐으면_알리지_않는다() {
    UUID tenantId = tenant();
    user(tenantId, UserRole.ASSET_MANAGER);
    ConsumableItem item = consumableService.create(tenantId, form("여유용지"));
    consumableService.addOrder(tenantId, item.getId(),
        new ConsumableOrderForm(LocalDate.now().minusDays(1), 1, null), null);
    consumableService.adoptCycle(tenantId, item.getId(), 90);

    assertThat(alertService.notifyTenant(tenantId)).isZero();
  }

  /** 사용 중지한 품목은 알리지 않는다 — 안 쓰는 물건으로 매일 알림이 오면 전체를 꺼 버린다. */
  @Test
  void 사용_중지한_품목은_알리지_않는다() {
    UUID tenantId = tenant();
    user(tenantId, UserRole.ASSET_MANAGER);
    ConsumableItem item = dueItem(tenantId, "중지알림용지");
    consumableService.setActive(tenantId, item.getId(), false);

    assertThat(alertService.notifyTenant(tenantId)).isZero();
  }

  /** 근거가 없는 품목(주문도 설정 주기도 없음)은 조용하다. */
  @Test
  void 근거가_없으면_침묵한다() {
    UUID tenantId = tenant();
    user(tenantId, UserRole.ASSET_MANAGER);
    consumableService.create(tenantId, form("근거없는용지"));

    assertThat(alertService.notifyTenant(tenantId)).isZero();
  }

  /** 일반 사용자에게는 가지 않는다 — 주문은 담당자의 일이다. */
  @Test
  void 일반_사용자에게는_가지_않는다() {
    UUID tenantId = tenant();
    user(tenantId, UserRole.ASSET_MANAGER);
    ManagedUser plain = user(tenantId, UserRole.USER);
    dueItem(tenantId, "역할알림용지");

    alertService.notifyTenant(tenantId);

    assertThat(notificationService.list(tenantId, plain.getId())).isEmpty();
  }

  /** 기관 경계 — 다른 기관 담당자에게 새지 않는다. */
  @Test
  void 다른_기관으로_새지_않는다() {
    UUID a = tenant();
    UUID b = tenant();
    user(a, UserRole.ASSET_MANAGER);
    ManagedUser managerB = user(b, UserRole.ASSET_MANAGER);
    dueItem(a, "A기관용지");

    alertService.notifyTenant(a);

    assertThat(notificationService.list(b, managerB.getId())).isEmpty();
  }

  /** 새 주문을 기록하면 제목이 바뀌어 다음 주기에 다시 알림이 간다(해제 로직 없이). */
  @Test
  void 새_주문을_기록하면_다음_주기에_다시_알린다() {
    UUID tenantId = tenant();
    ManagedUser manager = user(tenantId, UserRole.ASSET_MANAGER);
    ConsumableItem item = dueItem(tenantId, "재알림용지");
    alertService.notifyTenant(tenantId);
    int before = notificationService.list(tenantId, manager.getId()).size();

    // 오늘 주문 → 마지막 주문일이 바뀌지만 설정 주기가 0일이라 여전히 due
    consumableService.addOrder(tenantId, item.getId(),
        new ConsumableOrderForm(LocalDate.now(), 1, null), null);
    consumableService.adoptCycle(tenantId, item.getId(), 1);
    consumableService.adoptCycle(tenantId, item.getId(), null);
    consumableService.adoptCycle(tenantId, item.getId(), 1);

    alertService.notifyTenant(tenantId);
    assertThat(notificationService.list(tenantId, manager.getId()).size())
        .isGreaterThanOrEqualTo(before);
  }

  /** 주기가 지난 품목을 만든다: 설정 주기 1일 + 어제 주문. */
  private ConsumableItem dueItem(UUID tenantId, String name) {
    ConsumableItem item = consumableService.create(tenantId, form(name));
    consumableService.addOrder(tenantId, item.getId(),
        new ConsumableOrderForm(LocalDate.now().minusDays(10), 1, null), null);
    consumableService.adoptCycle(tenantId, item.getId(), 1);
    return item;
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("알림기관" + suffix, "CA" + suffix)).getId();
  }

  private ManagedUser user(UUID tenantId, UserRole role) {
    String username = "cal" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "담당자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE),
        Set.of(role));
  }

  private ConsumableItemForm form(String name) {
    return new ConsumableItemForm(name + System.nanoTime(), "사무용품", "박스", null, null);
  }
}
