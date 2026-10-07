package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 자산 목록의 검색·필터.
 *
 * <p>목록에서 행별 드롭다운을 걷어낸 뒤 병목이 <b>찾는 일</b>로 옮겨 갔다 — 130줄에서
 * 노트북 한 대를 눈으로 찾는 것이 가장 느리다. 거르기는 DB 가 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryFilterTest {

  @Autowired private InventoryItemService inventoryService;
  @Autowired private TenantService tenantService;

  @Test
  @DisplayName("이름·시리얼·분류 어디로든 찾는다")
  void 세_칸으로_찾는다() {
    UUID tenant = newTenant();
    inventoryService.create(tenant, new InventoryItemForm(
        "영업 노트북", InventoryItemType.PHYSICAL, "실물 / 사무기기 / 컴퓨터", "SN-AAA1",
        null, null));
    inventoryService.create(tenant, new InventoryItemForm(
        "회계 모니터", InventoryItemType.PHYSICAL, "실물 / 사무기기 / 모니터", "SN-BBB2",
        null, null));

    assertThat(names(inventoryService.filter(tenant, "노트북", null, null, false, 0).getContent()))
        .containsExactly("영업 노트북");
    assertThat(names(inventoryService.filter(tenant, "BBB2", null, null, false, 0).getContent()))
        .containsExactly("회계 모니터");
    assertThat(names(inventoryService.filter(tenant, "모니터", null, null, false, 0).getContent()))
        .as("분류 경로에도 '모니터' 가 있다")
        .containsExactly("회계 모니터");
  }

  @Test
  @DisplayName("대소문자를 가리지 않는다 — 시리얼은 사람이 손으로 친다")
  void 대소문자를_가리지_않는다() {
    UUID tenant = newTenant();
    inventoryService.create(tenant, new InventoryItemForm(
        "Dell Latitude", InventoryItemType.PHYSICAL, "실물 / 가구", "sn-lower-1", null, null));

    assertThat(inventoryService.filter(tenant, "dell", null, null, false, 0).getContent()).hasSize(1);
    assertThat(inventoryService.filter(tenant, "SN-LOWER", null, null, false, 0).getContent()).hasSize(1);
  }

  @Test
  @DisplayName("빈 검색어는 조건 없음이다 — 공백만 쳐도 전체가 나온다")
  void 빈_검색어는_전체다() {
    UUID tenant = newTenant();
    inventoryService.create(tenant, new InventoryItemForm(
        "자산1", InventoryItemType.PHYSICAL, "실물 / 가구", null, null, null));

    assertThat(inventoryService.filter(tenant, null, null, null, false, 0).getContent()).hasSize(1);
    assertThat(inventoryService.filter(tenant, "", null, null, false, 0).getContent()).hasSize(1);
    assertThat(inventoryService.filter(tenant, "   ", null, null, false, 0).getContent()).hasSize(1);
  }

  @Test
  @DisplayName("⚠️ 폐기품은 기본으로 빠진다 — 켜야 보인다")
  void 폐기품은_기본으로_빠진다() {
    UUID tenant = newTenant();
    InventoryItem item = inventoryService.create(tenant, new InventoryItemForm(
        "폐기대상", InventoryItemType.PHYSICAL, "실물 / 가구", null, null, null));
    inventoryService.retire(tenant, item.getId());

    assertThat(inventoryService.filter(tenant, null, null, null, false, 0).getContent()).isEmpty();
    assertThat(names(inventoryService.filter(tenant, null, null, null, true, 0).getContent()))
        .containsExactly("폐기대상");
    assertThat(inventoryService.retiredCount(tenant)).isEqualTo(1);
  }

  @Test
  @DisplayName("상태·유형 필터가 검색어와 함께 걸린다")
  void 조건이_겹쳐_걸린다() {
    UUID tenant = newTenant();
    inventoryService.create(tenant, new InventoryItemForm(
        "공용 노트북", InventoryItemType.PHYSICAL, "실물 / 사무기기 / 컴퓨터", null, null, null));
    inventoryService.create(tenant, new InventoryItemForm(
        "노트북 라이선스", InventoryItemType.SOFTWARE, "SW / 오피스", null, null, null));

    assertThat(names(inventoryService.filter(tenant, "노트북", null, null, false, 0).getContent()))
        .hasSize(2);
    assertThat(names(inventoryService.filter(
        tenant, "노트북", null, InventoryItemType.SOFTWARE, false, 0).getContent()))
        .containsExactly("노트북 라이선스");
    assertThat(names(inventoryService.filter(
        tenant, null, InventoryItemStatus.AVAILABLE, InventoryItemType.PHYSICAL, false, 0).getContent()))
        .containsExactly("공용 노트북");
  }

  @Test
  @DisplayName("⚠️ 검색이 기관을 넘지 않는다")
  void 검색은_기관을_넘지_않는다() {
    UUID mine = newTenant();
    UUID other = newTenant();
    inventoryService.create(other, new InventoryItemForm(
        "남의 노트북", InventoryItemType.PHYSICAL, "실물 / 가구", "SN-OTHER", null, null));

    assertThat(inventoryService.filter(mine, "노트북", null, null, false, 0).getContent()).isEmpty();
    assertThat(inventoryService.filter(mine, "SN-OTHER", null, null, false, 0).getContent()).isEmpty();
    assertThat(inventoryService.filter(other, "노트북", null, null, false, 0).getContent()).hasSize(1);
  }

  @Test
  @DisplayName("LIKE 와일드카드를 넣어도 전체가 쏟아지지 않는다")
  void 와일드카드는_글자로_취급한다() {
    UUID tenant = newTenant();
    inventoryService.create(tenant, new InventoryItemForm(
        "보통자산", InventoryItemType.PHYSICAL, "실물 / 가구", null, null, null));

    // '%' 를 그대로 넘기면 전체가 매치된다. 이름에 '%' 가 든 자산은 없으므로 0건이어야 한다.
    assertThat(inventoryService.filter(tenant, "%", null, null, false, 0).getContent())
        .as("LIKE 와일드카드가 글자로 취급되지 않으면 전체가 쏟아진다")
        .isEmpty();
    assertThat(inventoryService.filter(tenant, "_", null, null, false, 0).getContent()).isEmpty();
  }

  private static List<String> names(List<InventoryItem> items) {
    return items.stream().map(InventoryItem::getName).toList();
  }

  private UUID newTenant() {
    return tenantService.createTenant(
        new CreateTenantCommand("검색사", "SR" + System.nanoTime())).getId();
  }
}
