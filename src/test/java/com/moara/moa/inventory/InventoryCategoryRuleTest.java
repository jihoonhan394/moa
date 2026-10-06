package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.category.CategoryDomain;
import com.moara.moa.category.CategoryService;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 분류 판정 규칙. 자산을 만드는 길 넷(PC 등록·수정, CSV 임포트, 모바일 QR)이 모두 이걸 쓰므로,
 * 경계는 여기서 한 번만 짚는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryCategoryRuleTest {

  @Autowired private InventoryCategoryRule rule;
  @Autowired private CategoryService categoryService;
  @Autowired private TenantService tenantService;

  @Test
  @DisplayName("전체 경로는 그대로 통과하고 유형은 최상위에서 나온다")
  void 전체_경로는_통과한다() {
    UUID tenant = newTenant();

    assertThat(rule.resolve(tenant, "실물 / 사무기기 / 모니터"))
        .satisfies(r -> {
          assertThat(r.ok()).isTrue();
          assertThat(r.path()).isEqualTo("실물 / 사무기기 / 모니터");
          assertThat(r.type()).isEqualTo(InventoryItemType.PHYSICAL);
        });
    assertThat(rule.resolve(tenant, "SW / 오피스").type()).isEqualTo(InventoryItemType.SOFTWARE);
  }

  @Test
  @DisplayName("중간 노드와 최상위도 유효하다 — 굵게 분류하는 것도 분류다")
  void 중간_노드도_통과한다() {
    UUID tenant = newTenant();

    assertThat(rule.resolve(tenant, "실물 / 사무기기").ok()).isTrue();
    assertThat(rule.resolve(tenant, "실물").ok()).isTrue();
  }

  @Test
  @DisplayName("하나로 정해지는 짧은 이름은 전체 경로로 바꿔 준다")
  void 짧은_이름은_경로가_된다() {
    UUID tenant = newTenant();

    assertThat(rule.resolve(tenant, "모니터").path()).isEqualTo("실물 / 사무기기 / 모니터");
    assertThat(rule.resolve(tenant, "포토샵").path()).isEqualTo("SW / 디자인 / 포토샵");
    assertThat(rule.resolve(tenant, "  모니터  ").path())
        .as("앞뒤 공백은 사람이 CSV 를 쓸 때 늘 들어간다")
        .isEqualTo("실물 / 사무기기 / 모니터");
  }

  @Test
  @DisplayName("⚠️ 최상위만 맞는 경로는 거부한다 — 이게 원래의 구멍이었다")
  void 최상위만_맞으면_거부한다() {
    UUID tenant = newTenant();

    assertThat(rule.resolve(tenant, "실물 / 없는분류 / 더없음"))
        .satisfies(r -> {
          assertThat(r.ok()).isFalse();
          assertThat(r.error()).isEqualTo(InventoryCategoryRule.UNKNOWN);
          assertThat(r.path()).as("거부면 경로를 주지 않는다").isNull();
        });
  }

  @Test
  @DisplayName("없는 이름은 '없는 분류'다 — '선택하세요'가 아니다")
  void 없는_이름은_없는_분류다() {
    UUID tenant = newTenant();

    // 경로를 먼저 보고 유형을 나중에 뽑는 순서가 여기서 드러난다. 순서를 뒤집으면
    // 짧은 이름이 최상위 검사에 걸려 "분류를 선택하세요"로 나가 안내가 틀린다.
    assertThat(rule.resolve(tenant, "노트북").error()).isEqualTo(InventoryCategoryRule.UNKNOWN);
  }

  @Test
  @DisplayName("비어 있으면 '선택하세요'다")
  void 비면_선택하라고_한다() {
    UUID tenant = newTenant();

    assertThat(rule.resolve(tenant, null).error()).isEqualTo(InventoryCategoryRule.MISSING);
    assertThat(rule.resolve(tenant, "").error()).isEqualTo(InventoryCategoryRule.MISSING);
    assertThat(rule.resolve(tenant, "   ").error()).isEqualTo(InventoryCategoryRule.MISSING);
  }

  @Test
  @DisplayName("같은 이름이 둘이면 후보를 보여 주고 거부한다")
  void 이름이_겹치면_거부한다() {
    UUID tenant = newTenant();
    categoryService.create(tenant, CategoryDomain.ASSET, "모니터", rootId(tenant, "SW"));

    assertThat(rule.resolve(tenant, "모니터").error())
        .contains("같은 이름의 분류가 여럿입니다")
        .contains("실물 / 사무기기 / 모니터")
        .contains("SW / 모니터");

    // 전체 경로로 적으면 애매하지 않다.
    assertThat(rule.resolve(tenant, "SW / 모니터").type()).isEqualTo(InventoryItemType.SOFTWARE);
  }

  @Test
  @DisplayName("⚠️ 분류는 기관을 넘지 않는다 — 남의 기관 분류로 등록할 수 없다")
  void 분류는_기관을_넘지_않는다() {
    UUID mine = newTenant();
    UUID other = newTenant();
    categoryService.create(other, CategoryDomain.ASSET, "남의분류", rootId(other, "실물"));

    assertThat(rule.resolve(other, "남의분류").ok()).isTrue();
    assertThat(rule.resolve(mine, "남의분류").error()).isEqualTo(InventoryCategoryRule.UNKNOWN);
  }

  @Test
  @DisplayName("색인을 한 번 만들어 여러 건에 돌려 쓴다 — 행마다 트리를 다시 만들지 않는다")
  void 색인은_돌려_쓴다() {
    UUID tenant = newTenant();
    InventoryCategoryRule.CategoryIndex index = rule.index(tenant);

    assertThat(rule.resolve("모니터", index).path()).isEqualTo("실물 / 사무기기 / 모니터");
    assertThat(rule.resolve("프린터", index).path()).isEqualTo("실물 / 사무기기 / 프린터");
    assertThat(rule.resolve("없는것", index).ok()).isFalse();
  }

  private UUID newTenant() {
    return tenantService.createTenant(
        new CreateTenantCommand("분류사", "CAT" + System.nanoTime())).getId();
  }

  private UUID rootId(UUID tenantId, String name) {
    return categoryService.assetTree(tenantId).stream()
        .filter(node -> node.depth() == 0 && node.name().equals(name))
        .map(CategoryService.CategoryNode::id)
        .findFirst()
        .orElseThrow();
  }
}
