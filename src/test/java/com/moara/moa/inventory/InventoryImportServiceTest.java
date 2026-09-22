package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 인벤토리 CSV 대량 등록: 헤더 스킵·유형/날짜 파싱·부분 성공(중복·형식오류 행 보고) 검증. */
@SpringBootTest
@ActiveProfiles("test")
class InventoryImportServiceTest {
  @Autowired private InventoryImportService importService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private TenantService tenantService;

  @Test
  void importsRowsSkippingHeaderAndParsingTypesDates() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("자산사", "IMP" + System.nanoTime()));
    String csv = """
        이름,유형,카테고리,시리얼,만료일,구매일,보증만료,리스만료,비고
        영업 노트북,실물,노트북,SN-1,,2026-01-15,2028-01-15,,영업1팀
        오피스 라이선스,SW,라이선스,,2027-03-31,,,,연간구독
        "모니터, 27인치",PHYSICAL,모니터,SN-2,,,,,"큰따옴표, 쉼표 포함"
        """;

    InventoryImportResult result = importService.importCsv(tenant.getId(), csv);

    assertThat(result.created()).isEqualTo(3);
    assertThat(result.errors()).isEmpty();
    List<InventoryItem> items = inventoryService.findAll(tenant.getId());
    assertThat(items).hasSize(3);
    assertThat(items).anyMatch(i -> i.getName().equals("모니터, 27인치") && i.getType() == InventoryItemType.PHYSICAL);
    assertThat(items).anyMatch(i -> i.getName().equals("오피스 라이선스")
        && i.getType() == InventoryItemType.SOFTWARE
        && LocalDate.of(2027, 3, 31).equals(i.getExpiresAt()));
    assertThat(items).anyMatch(i -> i.getName().equals("영업 노트북")
        && LocalDate.of(2028, 1, 15).equals(i.getWarrantyEnds()));
  }

  @Test
  void reportsDuplicateAndBadRowsButImportsTheRest() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("자산사", "IMP" + System.nanoTime()));
    inventoryService.create(tenant.getId(),
        new InventoryItemForm("기존 노트북", InventoryItemType.PHYSICAL, null, null, null, null));

    String csv = """
        새 노트북,실물,,,,,,,
        기존 노트북,실물,,,,,,,
        불량행,이상한유형,,,,,,,
        ,실물,,,,,,,
        날짜불량,실물,,,2026-13-99,,,,
        """;

    InventoryImportResult result = importService.importCsv(tenant.getId(), csv);

    assertThat(result.created()).isEqualTo(1); // 새 노트북만 성공
    assertThat(result.errors()).hasSize(4);
    assertThat(result.errors()).anyMatch(e -> e.message().contains("이미 존재"));
    assertThat(result.errors()).anyMatch(e -> e.message().contains("알 수 없는 유형"));
    assertThat(result.errors()).anyMatch(e -> e.message().contains("이름이 비어"));
    assertThat(result.errors()).anyMatch(e -> e.message().contains("날짜 형식"));
  }
}
