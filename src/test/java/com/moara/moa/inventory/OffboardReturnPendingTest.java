package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 퇴사 처리는 <b>물건이 돌아왔다고 기록하지 않는다.</b>
 *
 * <p>전에는 배정된 자산을 곧바로 가용으로 바꾸고 장부에 "창고 입고" 구간을 열었다. 아무도
 * 물건을 보지 않았는데 장부가 "창고에 있음"이라고 말한 것이다 — 그리고 그 기록은 실제로
 * 확인한 구간과 구별되지 않았다. 기록이 없는 것보다 나쁘다. 확신을 가지고 틀리기 때문이다.
 *
 * <p>인수 확인({@code CustodyConfirmationTest})이 "관리자 한쪽 주장만 남는 것"을 막으려고
 * 생겼는데, 하필 분쟁 가능성이 가장 높은 퇴사 경로가 그 장치를 우회하고 있었다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OffboardReturnPendingTest {
  @Autowired private InventoryItemService inventoryService;
  @Autowired private InventoryCustodyService custodyService;
  @Autowired private InventoryOffboardHandler offboardHandler;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  /** 핵심: 퇴사 처리가 창고 구간을 열지 않는다. 물건은 아직 그 사람에게 있다. */
  @Test
  void 퇴사해도_창고에_들어왔다고_기록하지_않는다() {
    UUID tenantId = tenant();
    ManagedUser leaver = user(tenantId);
    InventoryItem item = item(tenantId, "퇴사자노트북");
    inventoryService.assign(tenantId, item.getId(), leaver.getId(), null);

    offboardHandler.offboard(tenantId, leaver.getId());

    InventoryCustody active = custodyService.current(tenantId, item.getId()).orElseThrow();
    assertThat(active.getHolderType())
        .as("퇴사만으로 창고 입고가 기록됐다 — 아무도 물건을 보지 않았다")
        .isEqualTo(InventoryHolderType.USER);
    assertThat(active.getHolderId()).isEqualTo(leaver.getId());
  }

  /** 가용으로 두면 다음 사람에게 배정되고, 받으러 간 사람은 아무것도 찾지 못한다. */
  @Test
  void 반납_대기는_가용이_아니다() {
    UUID tenantId = tenant();
    ManagedUser leaver = user(tenantId);
    InventoryItem item = item(tenantId, "미반납노트북");
    inventoryService.assign(tenantId, item.getId(), leaver.getId(), null);

    offboardHandler.offboard(tenantId, leaver.getId());

    InventoryItem after = inventoryService.findById(tenantId, item.getId());
    assertThat(after.getStatus()).isEqualTo(InventoryItemStatus.RETURN_PENDING);
    assertThat(inventoryService.returnPending(tenantId))
        .extracting(InventoryItem::getId).contains(item.getId());
  }

  /**
   * 배정을 풀지 않는다. 풀면 장부가 "아무도 안 갖고 있다"고 말하게 되는데 그건 사실이 아니다 —
   * 누구에게 받아야 하는지가 사라진다.
   */
  @Test
  void 누가_갖고_있는지는_그대로_남는다() {
    UUID tenantId = tenant();
    ManagedUser leaver = user(tenantId);
    InventoryItem item = item(tenantId, "보유자유지노트북");
    inventoryService.assign(tenantId, item.getId(), leaver.getId(), null);

    offboardHandler.offboard(tenantId, leaver.getId());

    assertThat(inventoryService.findById(tenantId, item.getId()).getAssignedUserId())
        .as("퇴사자에게 받아야 한다는 사실이 사라졌다")
        .isEqualTo(leaver.getId());
  }

  /** 실물을 확인한 사람이 창고 입고를 기록하면 그때 구간이 닫히고 가용이 된다. */
  @Test
  void 실물을_확인하면_비로소_창고로_들어온다() {
    UUID tenantId = tenant();
    ManagedUser leaver = user(tenantId);
    InventoryItem item = item(tenantId, "확인후입고노트북");
    inventoryService.assign(tenantId, item.getId(), leaver.getId(), null);
    offboardHandler.offboard(tenantId, leaver.getId());

    inventoryService.reclaim(tenantId, item.getId(), null, "퇴사 반납 실물 확인");

    InventoryItem after = inventoryService.findById(tenantId, item.getId());
    assertThat(after.getStatus()).isEqualTo(InventoryItemStatus.AVAILABLE);
    assertThat(after.getAssignedUserId()).isNull();
    assertThat(custodyService.current(tenantId, item.getId()).orElseThrow().getHolderType())
        .isEqualTo(InventoryHolderType.WAREHOUSE);
    assertThat(inventoryService.returnPending(tenantId))
        .extracting(InventoryItem::getId).doesNotContain(item.getId());
  }

  /** 퇴사 결과는 "회수했다"가 아니라 "확인이 필요하다"로 보여야 한다. */
  @Test
  void 결과_라벨이_확인이_필요함을_말한다() {
    UUID tenantId = tenant();
    ManagedUser leaver = user(tenantId);
    InventoryItem item = item(tenantId, "라벨노트북");
    inventoryService.assign(tenantId, item.getId(), leaver.getId(), null);

    var outcome = offboardHandler.offboard(tenantId, leaver.getId());

    assertThat(outcome.count()).isEqualTo(1);
    assertThat(outcome.label()).contains("확인");
  }

  /** 배정된 자산이 없으면 아무 일도 일어나지 않는다. */
  @Test
  void 배정_자산이_없으면_0건() {
    UUID tenantId = tenant();
    ManagedUser leaver = user(tenantId);

    assertThat(offboardHandler.offboard(tenantId, leaver.getId()).count()).isZero();
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(
        new CreateTenantCommand("퇴사기관" + suffix, "OB" + suffix)).getId();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "leave" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "퇴사자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private InventoryItem item(UUID tenantId, String name) {
    return inventoryService.create(tenantId, new InventoryItemForm(
        name + System.nanoTime(), InventoryItemType.PHYSICAL, "노트북",
        "SN-" + System.nanoTime(), null, null));
  }
}
