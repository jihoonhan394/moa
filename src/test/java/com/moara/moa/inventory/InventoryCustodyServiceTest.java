package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 자산 보관 장부. 검증의 초점은 <b>이력이 저절로 쌓이는가</b>다 — 배정·회수·폐기를 하는 사람이
 * 장부를 따로 기록하지 않아도 남아야 하고, 남지 않으면 "이 장비가 누구 손을 거쳤나"라는
 * 질문에 영원히 답할 수 없다(지난 일은 나중에 복원할 수 없다).
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryCustodyServiceTest {
  @Autowired private InventoryItemService inventoryService;
  @Autowired private InventoryCustodyService custodyService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  /** 배정·회수·폐기가 장부를 남긴다. 호출자는 장부를 모르고도 이력이 생긴다. */
  @Test
  void 배정_회수_폐기가_구간으로_쌓인다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    UUID itemId = item(tenantId, "이력노트북").getId();

    inventoryService.assign(tenantId, itemId, owner.getId(), owner.getId());
    inventoryService.reclaim(tenantId, itemId, owner.getId(), "회수");
    inventoryService.retire(tenantId, itemId, owner.getId());

    List<InventoryCustody> history = custodyService.history(tenantId, itemId);
    assertThat(history).hasSize(3);
    // 최근이 위 — 폐기 → 창고 → 사용자 순
    assertThat(history.get(0).getHolderType()).isEqualTo(InventoryHolderType.DISPOSED);
    assertThat(history).extracting(InventoryCustody::getHolderType)
        .containsExactlyInAnyOrder(InventoryHolderType.DISPOSED, InventoryHolderType.WAREHOUSE,
            InventoryHolderType.USER);
  }

  /** 새 구간이 열리면 이전 구간이 닫힌다. 열린 구간이 둘이면 "지금 어디 있나"에 답이 둘이 된다. */
  @Test
  void 열린_구간은_항상_하나다() {
    UUID tenantId = tenant();
    ManagedUser a = user(tenantId);
    ManagedUser b = user(tenantId);
    UUID itemId = item(tenantId, "단일구간노트북").getId();

    inventoryService.assign(tenantId, itemId, a.getId(), null);
    inventoryService.assign(tenantId, itemId, b.getId(), null);

    List<InventoryCustody> open =
        custodyService.history(tenantId, itemId).stream().filter(InventoryCustody::isOpen).toList();
    assertThat(open).hasSize(1);
    assertThat(open.get(0).getHolderId()).isEqualTo(b.getId());
    assertThat(custodyService.current(tenantId, itemId)).get()
        .extracting(InventoryCustody::getHolderId).isEqualTo(b.getId());
  }

  /**
   * 같은 사람에게 다시 배정해도 구간이 늘지 않는다. 화면에서 같은 버튼을 두 번 눌렀을 때
   * 의미 없는 구간이 쌓이면 이력을 읽을 수 없게 된다.
   */
  @Test
  void 같은_보관자로_다시_옮기면_구간이_늘지_않는다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    UUID itemId = item(tenantId, "중복배정노트북").getId();

    inventoryService.assign(tenantId, itemId, owner.getId(), null);
    inventoryService.assign(tenantId, itemId, owner.getId(), null);

    assertThat(custodyService.history(tenantId, itemId)).hasSize(1);
  }

  /**
   * 퇴사 반납은 <b>실물을 확인한 시점에</b> 장부에 남는다. 사유가 맥락을 남기고, 배치가 아닌
   * 사람이 확인했더라도 행위자를 모르면 null이다.
   *
   * <p>이 테스트는 전에 "퇴사 처리만으로 창고 구간이 생긴다"를 검증했다 — 즉 결함을 고정하고
   * 있었다. 아무도 물건을 보지 않았는데 장부가 창고에 있다고 말하던 동작이다.
   */
  @Test
  void 퇴사_반납은_실물_확인_시점에_남는다() {
    UUID tenantId = tenant();
    ManagedUser leaver = user(tenantId);
    UUID itemId = item(tenantId, "퇴사노트북").getId();
    inventoryService.assign(tenantId, itemId, leaver.getId(), null);

    inventoryService.requestReturnFrom(tenantId, leaver.getId());
    assertThat(custodyService.current(tenantId, itemId).orElseThrow().getHolderType())
        .as("퇴사 처리만으로 창고 입고가 기록됐다")
        .isEqualTo(InventoryHolderType.USER);

    inventoryService.reclaim(tenantId, itemId, null, "퇴사 회수");

    InventoryCustody latest = custodyService.history(tenantId, itemId).get(0);
    assertThat(latest.getHolderType()).isEqualTo(InventoryHolderType.WAREHOUSE);
    assertThat(latest.getReason()).isEqualTo("퇴사 회수");
    assertThat(latest.getCreatedBy()).isNull();
  }

  /** 장부도 기관 경계를 지킨다 — 다른 기관의 이력이 섞이면 그 자체로 보안 사고다. */
  @Test
  void 다른_기관의_이력은_보이지_않는다() {
    UUID tenantA = tenant();
    UUID tenantB = tenant();
    ManagedUser userA = user(tenantA);
    UUID itemA = item(tenantA, "A기관노트북").getId();
    inventoryService.assign(tenantA, itemA, userA.getId(), null);

    assertThat(custodyService.history(tenantA, itemA)).isNotEmpty();
    assertThat(custodyService.history(tenantB, itemA)).isEmpty();
    assertThat(custodyService.current(tenantB, itemA)).isEmpty();
    assertThat(custodyService.open(tenantB)).isEmpty();
  }

  /** 반납 예정일이 지났는데 열려 있으면 초과로 잡힌다(2b 고객처 납품이 이 조회를 쓴다). */
  @Test
  void 반납_예정일이_지나면_초과로_잡힌다() {
    UUID tenantId = tenant();
    UUID itemId = item(tenantId, "반납초과노트북").getId();
    LocalDate yesterday = LocalDate.now().minusDays(1);

    custodyService.transfer(tenantId, itemId, InventoryHolderType.CUSTOMER, null, "A고객사",
        yesterday, "납품", null, null);

    assertThat(custodyService.returnOverdue(tenantId, LocalDate.now()))
        .extracting(InventoryCustody::getHolderName).containsExactly("A고객사");
    // 예정일이 없으면 초과라는 개념 자체가 없다.
    UUID other = item(tenantId, "예정일없는노트북").getId();
    custodyService.transfer(tenantId, other, InventoryHolderType.CUSTOMER, null, "B고객사",
        null, "납품", null, null);
    assertThat(custodyService.returnOverdue(tenantId, LocalDate.now()))
        .extracting(InventoryCustody::getHolderName).doesNotContain("B고객사");
  }

  /** 상태 캐시(assignedUserId)와 장부가 어긋나지 않는다 — 어긋나면 어느 쪽을 믿을지 알 수 없다. */
  @Test
  void 배정_캐시와_장부가_같은_사람을_가리킨다() {
    UUID tenantId = tenant();
    ManagedUser owner = user(tenantId);
    UUID itemId = item(tenantId, "캐시일치노트북").getId();

    inventoryService.assign(tenantId, itemId, owner.getId(), null);

    InventoryItem item = inventoryService.findById(tenantId, itemId);
    assertThat(item.getAssignedUserId()).isEqualTo(owner.getId());
    assertThat(item.getStatus()).isEqualTo(InventoryItemStatus.ASSIGNED);
    assertThat(custodyService.current(tenantId, itemId)).get()
        .extracting(InventoryCustody::getHolderId).isEqualTo(owner.getId());
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("장부기관" + suffix, "LG" + suffix)).getId();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "custody" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "보관자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private InventoryItem item(UUID tenantId, String name) {
    return inventoryService.create(tenantId, new InventoryItemForm(
        name + System.nanoTime(), InventoryItemType.PHYSICAL, "노트북",
        "SN-" + System.nanoTime(), null, null));
  }
}
