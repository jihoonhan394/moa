package com.moara.moa.asset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AssetServiceTest {
  @Autowired private AssetService assetService;
  @Autowired private TenantService tenantService;

  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Test
  void createsUpdatesAndDeletesAsset() {
    Asset created = assetService.create(MOA, form("sample-server", AssetStatus.ACTIVE));
    assertEquals(MOA, created.getTenantId());

    assertEquals("sample-server", assetService.findById(MOA, created.getId()).getName());

    Asset updated = assetService.update(MOA, created.getId(), form("updated-server", AssetStatus.DISABLED));
    assertEquals("updated-server", updated.getName());
    assertEquals(AssetStatus.DISABLED, updated.getStatus());

    assetService.delete(MOA, created.getId());
    assertThrows(AssetNotFoundException.class, () -> assetService.findById(MOA, created.getId()));
  }

  @Test
  void otherTenantCannotSeeOrModifyAsset() {
    Tenant other = tenantService.createTenant(
        new CreateTenantCommand("Acme " + System.nanoTime(), "ACME" + System.nanoTime()));
    Asset moaAsset = assetService.create(MOA, form("moa-only-" + System.nanoTime(), AssetStatus.ACTIVE));

    // 다른 테넌트는 조회/수정/삭제 대상으로 접근할 수 없다(존재 미노출 = NotFound).
    UUID otherTenant = other.getId();
    assertThrows(AssetNotFoundException.class, () -> assetService.findById(otherTenant, moaAsset.getId()));
    assertThrows(AssetNotFoundException.class,
        () -> assetService.update(otherTenant, moaAsset.getId(), form("hijack", AssetStatus.DISABLED)));
    assertThrows(AssetNotFoundException.class, () -> assetService.delete(otherTenant, moaAsset.getId()));

    // 목록/카운트도 테넌트별로 격리된다.
    assertTrue(assetService.findAll(MOA).stream().anyMatch(a -> a.getId().equals(moaAsset.getId())));
    assertFalse(assetService.findAll(otherTenant).stream().anyMatch(a -> a.getId().equals(moaAsset.getId())));
  }

  private AssetForm form(String name, AssetStatus status) {
    return new AssetForm(
        name, AssetType.SERVER, AssetProtocol.SSH, "192.0.2.10", 22, "", "LINUX", "test", status);
  }
}
