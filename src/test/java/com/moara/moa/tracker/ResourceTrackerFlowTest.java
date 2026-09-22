package com.moara.moa.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.expiration.ExpirationRow;
import com.moara.moa.expiration.ExpirationService;
import com.moara.moa.inventory.InventoryItem;
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

/** 자원 만기·점검(인벤토리/자산 공용)이 대시보드에 흐르고, 반복 완료가 다음 일정으로 롤되는지 검증. */
@SpringBootTest
@ActiveProfiles("test")
class ResourceTrackerFlowTest {
  @Autowired private ResourceTrackerService trackerService;
  @Autowired private ExpirationService expirationService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private AssetService assetService;
  @Autowired private TenantService tenantService;

  @Test
  void inventoryTrackerFlowsIntoDashboardWithItemName() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("자산사", "RT" + System.nanoTime()));
    InventoryItem item = inventoryService.create(tenant.getId(), new InventoryItemForm(
        "탕비실 정수기", InventoryItemType.PHYSICAL, "가전", "SN-1", null, "렌탈"));

    trackerService.add(tenant.getId(), TrackerTargetType.INVENTORY, item.getId(),
        "정수기 필터 교체", LocalDate.now().plusDays(10), 90);

    List<ExpirationRow> rows = expirationService.findAll(tenant.getId());
    assertThat(rows).anyMatch(r -> r.category().equals("정기점검")
        && r.label().equals("탕비실 정수기")
        && r.detail().contains("필터 교체"));
  }

  @Test
  void recurringDoneRollsToNextDueDate() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("자산사", "RT" + System.nanoTime()));
    InventoryItem item = inventoryService.create(tenant.getId(), new InventoryItemForm(
        "보일러", InventoryItemType.PHYSICAL, "설비", "SN-2", null, null));
    trackerService.add(tenant.getId(), TrackerTargetType.INVENTORY, item.getId(),
        "정기점검", LocalDate.now().minusDays(1), 30);

    ResourceTracker before = trackerService.forTarget(tenant.getId(), TrackerTargetType.INVENTORY, item.getId()).get(0);
    trackerService.markDone(tenant.getId(), before.getId());

    ResourceTracker after = trackerService.forTarget(tenant.getId(), TrackerTargetType.INVENTORY, item.getId()).get(0);
    assertThat(after.getLastDoneOn()).isEqualTo(LocalDate.now());
    assertThat(after.getDueOn()).isEqualTo(LocalDate.now().plusDays(30));
  }

  @Test
  void assetTrackerResolvesAssetName() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("인프라사", "RT" + System.nanoTime()));
    Asset asset = assetService.create(tenant.getId(), new AssetForm(
        "www.example.com", AssetType.WEBSITE, AssetProtocol.HTTPS, "www.example.com", 443,
        "https://www.example.com", "-", "대표 웹사이트", AssetStatus.ACTIVE));

    trackerService.upsertProbed(tenant.getId(), TrackerTargetType.ASSET, asset.getId(),
        "SSL 인증서 (www.example.com)", LocalDate.now().plusDays(20), ResourceTrackerService.SOURCE_SSL);

    List<ExpirationRow> rows = expirationService.findAll(tenant.getId());
    assertThat(rows).anyMatch(r -> r.label().equals("www.example.com")
        && r.detail().contains("SSL 자동감지"));

    // 재프로브 시 새 항목이 아니라 기존 항목의 만료일만 갱신(대상+source로 단일 유지).
    trackerService.upsertProbed(tenant.getId(), TrackerTargetType.ASSET, asset.getId(),
        "SSL 인증서 (www.example.com)", LocalDate.now().plusDays(60), ResourceTrackerService.SOURCE_SSL);
    List<ResourceTracker> trackers =
        trackerService.forTarget(tenant.getId(), TrackerTargetType.ASSET, asset.getId());
    assertThat(trackers).hasSize(1);
    assertThat(trackers.get(0).getDueOn()).isEqualTo(LocalDate.now().plusDays(60));
  }

  @Test
  void sslProbeStoresHostPortAndDetailForDailyRefresh() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("인프라사", "RT" + System.nanoTime()));
    Asset asset = assetService.create(tenant.getId(), new AssetForm(
        "svc.example.com", AssetType.WEBSITE, AssetProtocol.HTTPS, "svc.example.com", 8443,
        "https://svc.example.com", "-", "내부 서비스", AssetStatus.ACTIVE));

    trackerService.upsertProbed(tenant.getId(), TrackerTargetType.ASSET, asset.getId(),
        "SSL 인증서 (svc.example.com)", LocalDate.now().plusDays(15), ResourceTrackerService.SOURCE_SSL,
        "svc.example.com", 8443, "유효기간 2026-01-01 ~ 2026-08-24 · 발급자 Test CA");

    ResourceTracker tracker =
        trackerService.forTarget(tenant.getId(), TrackerTargetType.ASSET, asset.getId()).get(0);
    assertThat(tracker.getProbeHost()).isEqualTo("svc.example.com");
    assertThat(tracker.getProbePort()).isEqualTo(8443);
    assertThat(tracker.getDetail()).contains("발급자 Test CA");

    // 재프로브(host/port 갱신 경로)로 기존 항목이 갱신되는지.
    trackerService.upsertProbed(tenant.getId(), TrackerTargetType.ASSET, asset.getId(),
        "SSL 인증서 (svc.example.com)", LocalDate.now().plusDays(90), ResourceTrackerService.SOURCE_SSL,
        "svc.example.com", 8443, "유효기간 2026-01-01 ~ 2026-11-30 · 발급자 Test CA");
    List<ResourceTracker> after =
        trackerService.forTarget(tenant.getId(), TrackerTargetType.ASSET, asset.getId());
    assertThat(after).hasSize(1);
    assertThat(after.get(0).getDueOn()).isEqualTo(LocalDate.now().plusDays(90));
  }
}
