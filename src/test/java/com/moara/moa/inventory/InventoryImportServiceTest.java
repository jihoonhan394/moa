package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.category.CategoryDomain;
import com.moara.moa.category.CategoryService;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 인벤토리 CSV 대량 등록: 헤더 스킵·유형/날짜 파싱·부분 성공(중복·형식오류 행 보고)과
 * <b>분류 검사</b>.
 *
 * <p>⚠️ 분류 검사가 들어오면서 <b>동작이 바뀌었다.</b> 전에는 분류를 검사 없이 받아서
 * {@code 노트북} 처럼 트리에 없는 이름이 그대로 저장됐다 — 그러면 그 자산은 어느 분류
 * 필터에도 안 걸린다. 이제 없는 분류는 그 행을 거부한다(날짜·유형 오류와 같은 취급).
 * 이 테스트의 CSV 가 예전에 {@code 노트북}·{@code 라이선스} 를 쓰고 있었는데, 그 둘이
 * 바로 실재하지 않는 분류였다.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryImportServiceTest {
  @Autowired private InventoryImportService importService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private CategoryService categoryService;
  @Autowired private TenantService tenantService;

  @Test
  void importsRowsSkippingHeaderAndParsingTypesDates() {
    UUID tenant = newTenant();
    String csv = """
        이름,유형,카테고리,시리얼,만료일,구매일,보증만료,리스만료,비고
        영업 노트북,실물,실물 / 사무기기 / 컴퓨터,SN-1,,2026-01-15,2028-01-15,,영업1팀
        오피스 라이선스,SW,SW / 오피스,,2027-03-31,,,,연간구독
        "모니터, 27인치",PHYSICAL,실물 / 사무기기 / 모니터,SN-2,,,,,"큰따옴표, 쉼표 포함"
        """;

    InventoryImportResult result = importService.importCsv(tenant, csv);

    assertThat(result.errors()).isEmpty();
    assertThat(result.created()).isEqualTo(3);
    List<InventoryItem> items = inventoryService.findAll(tenant);
    assertThat(items).hasSize(3);
    assertThat(items).anyMatch(i -> i.getName().equals("모니터, 27인치")
        && i.getType() == InventoryItemType.PHYSICAL);
    assertThat(items).anyMatch(i -> i.getName().equals("오피스 라이선스")
        && i.getType() == InventoryItemType.SOFTWARE
        && LocalDate.of(2027, 3, 31).equals(i.getExpiresAt()));
    assertThat(items).anyMatch(i -> i.getName().equals("영업 노트북")
        && LocalDate.of(2028, 1, 15).equals(i.getWarrantyEnds()));
  }

  @Test
  void reportsDuplicateAndBadRowsButImportsTheRest() {
    UUID tenant = newTenant();
    inventoryService.create(tenant,
        new InventoryItemForm("기존 노트북", InventoryItemType.PHYSICAL, null, null, null, null));

    String csv = """
        새 노트북,실물,,,,,,,
        기존 노트북,실물,,,,,,,
        불량행,이상한유형,,,,,,,
        ,실물,,,,,,,
        날짜불량,실물,,,2026-13-99,,,,
        """;

    InventoryImportResult result = importService.importCsv(tenant, csv);

    assertThat(result.created()).isEqualTo(1); // 새 노트북만 성공
    assertThat(result.errors()).hasSize(4);
    assertThat(result.errors()).anyMatch(e -> e.message().contains("이미 존재"));
    assertThat(result.errors()).anyMatch(e -> e.message().contains("알 수 없는 유형"));
    assertThat(result.errors()).anyMatch(e -> e.message().contains("이름이 비어"));
    assertThat(result.errors()).anyMatch(e -> e.message().contains("날짜 형식"));
  }

  @Test
  @DisplayName("⚠️ 없는 분류는 그 행을 거부한다 — 전에는 검사 없이 들어가 어느 필터에도 안 걸렸다")
  void 없는_분류는_행을_거부한다() {
    UUID tenant = newTenant();
    String csv = """
        유령1,실물,노트북,,,,,,
        유령2,실물,실물 / 없는분류 / 더없음,,,,,,
        정상,실물,실물 / 가구,,,,,,
        """;

    InventoryImportResult result = importService.importCsv(tenant, csv);

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.errors()).hasSize(2);
    assertThat(result.errors()).allMatch(e -> e.message().contains("없는 분류입니다"));
    // 거부된 행은 저장되지 않았다 — 분류만 비워 넣는 식으로 끼워 두지 않는다.
    assertThat(inventoryService.findAll(tenant)).extracting(InventoryItem::getName)
        .containsExactly("정상");
  }

  @Test
  @DisplayName("짧은 이름은 전체 경로로 바꿔 저장한다 — 사람이 손으로 쓴 CSV 를 깨지 않는다")
  void 짧은_이름은_경로로_해석한다() {
    UUID tenant = newTenant();
    // 유형 열을 비워 둔다 — 분류에서 파생되는지 함께 본다.
    InventoryImportResult result = importService.importCsv(tenant, """
        사무용 모니터,,모니터,,,,,,
        포토샵 라이선스,,포토샵,,,,,,
        """);

    assertThat(result.errors()).isEmpty();
    assertThat(result.created()).isEqualTo(2);
    assertThat(inventoryService.findAll(tenant))
        .anyMatch(i -> i.getName().equals("사무용 모니터")
            && "실물 / 사무기기 / 모니터".equals(i.getCategory())
            && i.getType() == InventoryItemType.PHYSICAL)
        .anyMatch(i -> i.getName().equals("포토샵 라이선스")
            && "SW / 디자인 / 포토샵".equals(i.getCategory())
            && i.getType() == InventoryItemType.SOFTWARE);
  }

  @Test
  @DisplayName("⚠️ 유형과 분류가 모순되면 거부한다 — 그 자산은 두 집계가 영원히 어긋난다")
  void 유형과_분류가_모순되면_거부한다() {
    UUID tenant = newTenant();

    InventoryImportResult result = importService.importCsv(tenant, """
        모순자산,SW,실물 / 사무기기 / 모니터,,,,,,
        """);

    assertThat(result.created()).isZero();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).message()).contains("유형과 분류가 맞지 않습니다");
    assertThat(inventoryService.findAll(tenant)).isEmpty();
  }

  @Test
  @DisplayName("같은 이름의 분류가 둘이면 후보를 보여 주고 거부한다 — 우리가 고를 수 없다")
  void 이름이_겹치면_전체_경로를_요구한다() {
    UUID tenant = newTenant();
    // SW 아래에도 '모니터'를 만든다 → 짧은 이름 '모니터'는 더 이상 하나로 정해지지 않는다.
    categoryService.create(tenant, CategoryDomain.ASSET, "모니터", rootId(tenant, "SW"));

    InventoryImportResult result = importService.importCsv(tenant, """
        애매자산,,모니터,,,,,,
        """);

    assertThat(result.created()).isZero();
    assertThat(result.errors().get(0).message())
        .contains("같은 이름의 분류가 여럿입니다")
        .contains("실물 / 사무기기 / 모니터")
        .contains("SW / 모니터");
  }

  @Test
  @DisplayName("분류를 비운 행은 전처럼 통과한다 — 이미 쓰고 있는 CSV 를 깨지 않는다")
  void 분류가_비면_전처럼_통과한다() {
    UUID tenant = newTenant();

    InventoryImportResult result = importService.importCsv(tenant, """
        분류없음,실물,,,,,,,
        유형도없음,,,,,,,,
        """);

    assertThat(result.errors()).isEmpty();
    assertThat(result.created()).isEqualTo(2);
    assertThat(inventoryService.findAll(tenant))
        .allMatch(i -> i.getCategory() == null && i.getType() == InventoryItemType.PHYSICAL);
  }

  /** 기관마다 새로 만든다 — 분류 트리는 기관별이고, 첫 조회 때 기본값이 시드된다. */
  private UUID newTenant() {
    Tenant tenant = tenantService.createTenant(
        new CreateTenantCommand("자산사", "IMP" + System.nanoTime()));
    return tenant.getId();
  }

  private UUID rootId(UUID tenantId, String name) {
    return categoryService.assetTree(tenantId).stream()
        .filter(node -> node.depth() == 0 && node.name().equals(name))
        .map(CategoryService.CategoryNode::id)
        .findFirst()
        .orElseThrow();
  }
}
