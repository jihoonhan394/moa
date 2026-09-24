package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.notification.Notification;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 인수 확인. 장부가 <b>양쪽의 기록</b>이 되게 하는 장치다 — 받은 사람의 확인이 없으면
 * "저는 그 노트북 받은 적 없는데요"가 나왔을 때 관리자 한쪽 주장만 남는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CustodyConfirmationTest {
  @Autowired private InventoryItemService inventoryService;
  @Autowired private InventoryCustodyService custodyService;
  @Autowired private NotificationService notificationService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  /** 배정하면 받는 사람에게 알림이 간다. 알리지 않으면 확인을 누를 자리를 모른다. */
  @Test
  void 배정하면_받는_사람에게_알림이_간다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    InventoryItem item = item(tenantId, "알림노트북");

    inventoryService.assign(tenantId, item.getId(), owner.getId(), null);

    List<Notification> inbox = notificationService.list(tenantId, owner.getId());
    assertThat(inbox).anyMatch(n -> n.getTitle().contains(item.getName()));
    assertThat(inbox).anyMatch(n -> n.getLink().equals("/my/assets/" + item.getId()));
  }

  /** 배정 직후에는 확인 대기 상태다. */
  @Test
  void 배정_직후에는_확인_대기다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    InventoryItem item = item(tenantId, "대기노트북");
    inventoryService.assign(tenantId, item.getId(), owner.getId(), null);

    assertThat(custodyService.awaitingConfirmation(tenantId))
        .extracting(InventoryCustody::getItemId).contains(item.getId());
  }

  @Test
  void 본인이_확인하면_대기에서_빠진다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    InventoryItem item = item(tenantId, "확인노트북");
    inventoryService.assign(tenantId, item.getId(), owner.getId(), null);

    assertThat(custodyService.confirmReceipt(tenantId, item.getId(), owner.getId())).isTrue();

    assertThat(custodyService.awaitingConfirmation(tenantId))
        .extracting(InventoryCustody::getItemId).doesNotContain(item.getId());
    assertThat(custodyService.current(tenantId, item.getId())).get()
        .extracting(InventoryCustody::getConfirmedAt).isNotNull();
  }

  /**
   * 남이 대신 확인해 줄 수 없다. 대신 눌러 주면 장부가 다시 한쪽 기록이 되어 확인의 의미가
   * 사라진다 — 이 판정이 무너지면 기능 전체가 무의미하다.
   */
  @Test
  void 남이_대신_확인할_수_없다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    ManagedUser stranger = user(tenantId);
    InventoryItem item = item(tenantId, "대리확인노트북");
    inventoryService.assign(tenantId, item.getId(), owner.getId(), null);

    assertThat(custodyService.confirmReceipt(tenantId, item.getId(), stranger.getId())).isFalse();
    assertThat(custodyService.current(tenantId, item.getId())).get()
        .extracting(InventoryCustody::getConfirmedAt).isNull();
  }

  /** 두 번 확인해도 처음 시각이 유지된다 — 나중 값으로 밀리면 "언제 받았나"의 답이 바뀐다. */
  @Test
  void 두_번_확인해도_처음_시각이_남는다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    InventoryItem item = item(tenantId, "중복확인노트북");
    inventoryService.assign(tenantId, item.getId(), owner.getId(), null);
    custodyService.confirmReceipt(tenantId, item.getId(), owner.getId());
    var first = custodyService.current(tenantId, item.getId()).orElseThrow().getConfirmedAt();

    assertThat(custodyService.confirmReceipt(tenantId, item.getId(), owner.getId())).isFalse();
    assertThat(custodyService.current(tenantId, item.getId()).orElseThrow().getConfirmedAt())
        .isEqualTo(first);
  }

  /** 회수하면 그 구간이 닫히므로 확인 대기에서도 빠진다(확인할 대상이 아니게 된다). */
  @Test
  void 회수하면_확인_대기에서_빠진다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    InventoryItem item = item(tenantId, "회수노트북");
    inventoryService.assign(tenantId, item.getId(), owner.getId(), null);

    inventoryService.reclaim(tenantId, item.getId(), null, "회수");

    assertThat(custodyService.awaitingConfirmation(tenantId))
        .extracting(InventoryCustody::getItemId).doesNotContain(item.getId());
  }

  /** 창고·고객처 구간은 확인할 사람이 없어 대기 목록에 뜨지 않는다. */
  @Test
  void 사람이_아닌_보관자는_확인_대상이_아니다() {
    UUID tenantId = tenant();
    InventoryItem item = item(tenantId, "창고보관품");
    custodyService.toWarehouse(tenantId, item.getId(), "입고", null);

    assertThat(custodyService.awaitingConfirmation(tenantId))
        .extracting(InventoryCustody::getItemId).doesNotContain(item.getId());
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("인수기관" + suffix, "RC" + suffix)).getId();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "recv" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "수령자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private InventoryItem item(UUID tenantId, String name) {
    return inventoryService.create(tenantId, new InventoryItemForm(
        name + System.nanoTime(), InventoryItemType.PHYSICAL, "노트북",
        "SN-" + System.nanoTime(), null, null));
  }
}
