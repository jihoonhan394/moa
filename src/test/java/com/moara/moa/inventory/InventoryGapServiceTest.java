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

/** 대장 점검: 빠진 칸·오래 기록 없음·날짜 역전을 찾되, 폐기 자산은 보지 않는다. */
@SpringBootTest
@ActiveProfiles("test")
class InventoryGapServiceTest {
  @Autowired private InventoryGapService gapService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private InventoryCustodyService custodyService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void 빠진_칸과_날짜_역전을_찾고_폐기는_뺀다() {
    UUID tenantId = tenant();
    LocalDate today = LocalDate.now();
    InventoryItem bare = item(tenantId, "빈칸노트북", null, null);
    InventoryItem complete = item(tenantId, "완비노트북", today.minusYears(1), today.plusYears(2));
    InventoryItem reversed = item(tenantId, "역전노트북", today.minusMonths(1), today.minusYears(1));
    ManagedUser owner = user(tenantId);
    inventoryService.assign(tenantId, complete.getId(), owner.getId(), owner.getId());
    InventoryItem retired = item(tenantId, "폐기노트북", null, null);
    inventoryService.retire(tenantId, retired.getId(), owner.getId());

    List<InventoryGapService.Gap> gaps = gapService.gaps(tenantId, today);

    assertThat(names(gaps, "no-warranty")).containsExactly(bare.getName());
    assertThat(names(gaps, "no-owner")).isEmpty(); // 창고 재고(등록 직후 가용)는 책임자 점검에서 뺀다
    assertThat(names(gaps, "date-mismatch")).containsExactly(reversed.getName());
    assertThat(names(gaps, "stale")).isEmpty();
  }

  @Test
  void 책임자_없음은_창고_밖에_나간_것만_본다() {
    UUID tenantId = tenant();
    LocalDate today = LocalDate.now();
    InventoryItem shipped = item(tenantId, "납품장비", null, null);
    InventoryItem returned = item(tenantId, "회수장비", null, null);
    InventoryItem withDept = item(tenantId, "부서보관", null, null);
    // 고객처 반출은 상태를 '가용'으로 둔 채 나간다 — 상태가 아니라 보관 위치로 판단해야 잡힌다.
    custodyService.transfer(tenantId, shipped.getId(), InventoryHolderType.CUSTOMER, null, "고객사A",
        today.plusDays(30), "납품", null, null);
    custodyService.transfer(tenantId, returned.getId(), InventoryHolderType.CUSTOMER, null, "고객사A",
        null, "납품", null, null);
    custodyService.toWarehouse(tenantId, returned.getId(), "회수", null);
    custodyService.transfer(tenantId, withDept.getId(), InventoryHolderType.GROUP, UUID.randomUUID(), null,
        null, "부서 보관", null, null);

    assertThat(names(gapService.gaps(tenantId, today), "no-owner")).containsExactly(shipped.getName());
  }

  @Test
  void 일년_넘게_보관_기록이_없으면_실사_대상이다() {
    UUID tenantId = tenant();
    LocalDate today = LocalDate.now();
    InventoryItem idle = item(tenantId, "방치노트북", null, today.plusYears(3));

    // 시간을 앞당겨 본다: 오늘 등록·기록 없음 → 366일 뒤에는 1년 넘게 기록 없음.
    assertThat(names(gapService.gaps(tenantId, today), "stale")).isEmpty();
    List<InventoryGapService.Gap> later = gapService.gaps(tenantId, today.plusDays(366));
    assertThat(names(later, "stale")).containsExactly(idle.getName());
    assertThat(rows(later, "stale").get(0).detail()).startsWith("기록 없음");
  }

  @Test
  void 미래_취득일도_날짜_역전이다() {
    InventoryItem item = new InventoryItem(UUID.randomUUID(), UUID.randomUUID(), new InventoryItemForm(
        "미래", InventoryItemType.PHYSICAL, null, null, null,
        LocalDate.now().plusDays(10), null, null, null, null), java.time.OffsetDateTime.now());
    assertThat(InventoryGapService.dateMismatch(item, LocalDate.now())).isTrue();
  }

  private static List<InventoryGapService.Row> rows(List<InventoryGapService.Gap> gaps, String key) {
    return gaps.stream().filter(g -> g.key().equals(key)).findFirst().orElseThrow().rows();
  }

  private static List<String> names(List<InventoryGapService.Gap> gaps, String key) {
    return rows(gaps, key).stream().map(r -> r.item().getName()).toList();
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("점검기관" + suffix, "GP" + suffix)).getId();
  }

  private ManagedUser user(UUID tenantId) {
    String username = "gap" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "점검자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private InventoryItem item(UUID tenantId, String name, LocalDate purchaseDate, LocalDate warrantyEnds) {
    return inventoryService.create(tenantId, new InventoryItemForm(
        name + System.nanoTime(), InventoryItemType.PHYSICAL, "노트북", "SN-" + System.nanoTime(),
        null, purchaseDate, warrantyEnds, null, null, null));
  }
}
