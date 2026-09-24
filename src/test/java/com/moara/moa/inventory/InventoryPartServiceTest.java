package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 자산 구성품(부품). "PC에서 램을 빼서 다른 컴퓨터로 옮긴다"가 성립하는지, 그리고 그 과정에서
 * <b>대장이 깨지지 않는지</b>를 본다 — 실물은 있는데 장부에 없거나 그 반대인 상태가 실사에서
 * 가장 곤란하다.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryPartServiceTest {
  @Autowired private InventoryPartService partService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private InventoryCustodyService custodyService;
  @Autowired private TenantService tenantService;

  /** 장착 → 부모가 구성품으로 갖고, 부품의 보관 장부에도 남는다. */
  @Test
  void 부품을_장착하면_구성품이_되고_장부에_남는다() {
    UUID tenantId = tenant();
    UUID laptop = item(tenantId, "노트북", null, 1).getId();
    UUID ram = item(tenantId, "RAM 32GB", null, 1).getId();

    partService.attach(tenantId, ram, laptop, 0, null);

    assertThat(partService.partsOf(tenantId, laptop)).extracting(InventoryItem::getId)
        .containsExactly(ram);
    assertThat(inventoryService.findById(tenantId, ram).getParentItemId()).isEqualTo(laptop);
    assertThat(custodyService.current(tenantId, ram)).get()
        .extracting(InventoryCustody::getHolderType)
        .isEqualTo(InventoryHolderType.PARENT_ITEM);
  }

  /** 요청하신 시나리오: RAM 2개 중 1개만 다른 PC로. 원래 행은 1개로 줄고 새 행이 생긴다. */
  @Test
  void 수량_일부만_다른_장비로_옮긴다() {
    UUID tenantId = tenant();
    UUID laptopA = item(tenantId, "노트북A", null, 1).getId();
    UUID laptopB = item(tenantId, "노트북B", null, 1).getId();
    UUID ram = item(tenantId, "RAM 32GB", null, 2).getId();
    partService.attach(tenantId, ram, laptopA, 0, null); // 2개 모두 A에

    partService.attach(tenantId, ram, laptopB, 1, null); // 그중 1개를 B로

    assertThat(inventoryService.findById(tenantId, ram).getQuantity()).isEqualTo(1);
    assertThat(inventoryService.findById(tenantId, ram).getParentItemId()).isEqualTo(laptopA);
    var movedToB = partService.partsOf(tenantId, laptopB);
    assertThat(movedToB).hasSize(1);
    assertThat(movedToB.get(0).getQuantity()).isEqualTo(1);
    assertThat(movedToB.get(0).getName()).isEqualTo(inventoryService.findById(tenantId, ram).getName());
    assertThat(movedToB.get(0).getId()).isNotEqualTo(ram); // 갈라진 새 행
  }

  /** 시리얼이 있으면 쪼갤 수 없다 — 시리얼은 개체 하나를 가리키므로 "2개 중 1개"가 성립하지 않는다. */
  @Test
  void 시리얼이_있으면_쪼개지_않고_통째로_옮긴다() {
    UUID tenantId = tenant();
    UUID laptopA = item(tenantId, "본체A", null, 1).getId();
    UUID laptopB = item(tenantId, "본체B", null, 1).getId();
    UUID ssd = item(tenantId, "SSD 1TB", "SN-UNIQUE-1", 2).getId();
    partService.attach(tenantId, ssd, laptopA, 0, null);

    partService.attach(tenantId, ssd, laptopB, 1, null); // 1개만 요청해도

    assertThat(partService.partsOf(tenantId, laptopA)).isEmpty();
    assertThat(partService.partsOf(tenantId, laptopB)).extracting(InventoryItem::getId)
        .containsExactly(ssd);
  }

  /** 자기 자신·자손 안에는 넣을 수 없다. 고리가 생기면 구성품 조회가 무한히 돈다. */
  @Test
  void 순환_장착은_막힌다() {
    UUID tenantId = tenant();
    UUID outer = item(tenantId, "본체", null, 1).getId();
    UUID inner = item(tenantId, "보드", null, 1).getId();
    partService.attach(tenantId, inner, outer, 0, null);

    assertThatThrownBy(() -> partService.attach(tenantId, outer, inner, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("자기 자신");
    assertThatThrownBy(() -> partService.attach(tenantId, outer, outer, 0, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 깊이 상한. 장비 → 부품 → 하위부품까지고 그 이상은 이력을 읽기 어려워진다. */
  @Test
  void 계층은_3단계까지다() {
    UUID tenantId = tenant();
    UUID l1 = item(tenantId, "1단", null, 1).getId();
    UUID l2 = item(tenantId, "2단", null, 1).getId();
    UUID l3 = item(tenantId, "3단", null, 1).getId();
    UUID l4 = item(tenantId, "4단", null, 1).getId();
    partService.attach(tenantId, l2, l1, 0, null);
    partService.attach(tenantId, l3, l2, 0, null);

    assertThatThrownBy(() -> partService.attach(tenantId, l4, l3, 0, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3단계");
  }

  /**
   * 장비를 폐기해도 부품은 <b>같이 버려지지 않는다</b>. 멀쩡한 RAM이 장부에서 사라지면
   * 실물은 있는데 대장에만 없는 상태가 된다.
   */
  @Test
  void 장비_폐기_시_부품은_창고로_돌아온다() {
    UUID tenantId = tenant();
    UUID laptop = item(tenantId, "폐기예정노트북", null, 1).getId();
    UUID ram = item(tenantId, "살아남을RAM", null, 1).getId();
    partService.attach(tenantId, ram, laptop, 0, null);

    inventoryService.retire(tenantId, laptop, null);

    InventoryItem survivor = inventoryService.findById(tenantId, ram);
    assertThat(survivor.getParentItemId()).isNull();
    assertThat(survivor.getStatus()).isNotEqualTo(InventoryItemStatus.RETIRED);
    assertThat(custodyService.current(tenantId, ram)).get()
        .extracting(InventoryCustody::getHolderType).isEqualTo(InventoryHolderType.WAREHOUSE);
  }

  /** 다른 기관의 자산은 부품으로 붙일 수 없다 — 붙는 순간 교차 기관 노출이다. */
  @Test
  void 다른_기관_자산은_장착할_수_없다() {
    UUID tenantA = tenant();
    UUID tenantB = tenant();
    UUID laptopA = item(tenantA, "A기관본체", null, 1).getId();
    UUID ramB = item(tenantB, "B기관RAM", null, 1).getId();

    assertThatThrownBy(() -> partService.attach(tenantA, ramB, laptopA, 0, null))
        .isInstanceOf(InventoryItemNotFoundException.class);
    assertThat(partService.partsOf(tenantA, laptopA)).isEmpty();
  }

  /** 장착 후보에는 이미 장착된 것·자기 자신·자손·폐기품이 안 나온다. */
  @Test
  void 장착_후보에서_자기_자신과_이미_장착된_것을_뺀다() {
    UUID tenantId = tenant();
    UUID laptop = item(tenantId, "후보본체", null, 1).getId();
    UUID attached = item(tenantId, "이미장착", null, 1).getId();
    UUID free = item(tenantId, "미장착", null, 1).getId();
    partService.attach(tenantId, attached, laptop, 0, null);

    var candidates = partService.attachableTo(tenantId, laptop);
    assertThat(candidates).extracting(InventoryItem::getId).contains(free);
    assertThat(candidates).extracting(InventoryItem::getId).doesNotContain(laptop, attached);
  }

  /** 같은 이름의 부품이 여러 장비에 들어가도 된다(V66에서 유니크를 상위 자산으로 한정). */
  @Test
  void 같은_이름_부품이_여러_장비에_들어간다() {
    UUID tenantId = tenant();
    UUID a = item(tenantId, "본체1", null, 1).getId();
    UUID b = item(tenantId, "본체2", null, 1).getId();
    UUID ram = item(tenantId, "공용RAM", null, 2).getId();
    partService.attach(tenantId, ram, a, 1, null);
    partService.attach(tenantId, ram, b, 1, null);

    assertThat(partService.partsOf(tenantId, a)).hasSize(1);
    assertThat(partService.partsOf(tenantId, b)).hasSize(1);
  }

  private UUID tenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("부품기관" + suffix, "PT" + suffix)).getId();
  }

  private InventoryItem item(UUID tenantId, String name, String serial, int quantity) {
    return inventoryService.create(tenantId, new InventoryItemForm(
        name + "-" + System.nanoTime(), InventoryItemType.PHYSICAL, "부품", serial,
        null, null, null, null, null, quantity));
  }
}
