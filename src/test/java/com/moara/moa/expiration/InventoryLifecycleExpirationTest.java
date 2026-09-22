package com.moara.moa.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 인벤토리 라이프사이클: 보증·리스 만기가 만료 통합 대시보드에 자동 반영되는지 검증. */
@SpringBootTest
@ActiveProfiles("test")
class InventoryLifecycleExpirationTest {
  @Autowired private InventoryItemService inventoryService;
  @Autowired private ExpirationService expirationService;
  @Autowired private TenantService tenantService;

  @Test
  void warrantyAndLeaseExpiriesFlowIntoDashboard() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("자산사", "IL" + System.nanoTime()));
    inventoryService.create(tenant.getId(), new InventoryItemForm(
        "영업 노트북", InventoryItemType.PHYSICAL, "노트북", "SN-777",
        null,                       // SW 라이선스 만료 없음
        LocalDate.of(2026, 1, 1),   // 구매일
        LocalDate.of(2028, 1, 1),   // 보증 만료
        LocalDate.of(2027, 6, 30),  // 리스 만료
        "영업1팀"));

    List<ExpirationRow> rows = expirationService.findAll(tenant.getId());
    // 보증·리스 만기가 각각 한 줄씩 대시보드에 뜬다(대상=그 노트북).
    assertThat(rows).anyMatch(r -> r.category().equals("보증") && r.label().equals("영업 노트북"));
    assertThat(rows).anyMatch(r -> r.category().equals("리스/계약") && r.label().equals("영업 노트북"));
  }
}
