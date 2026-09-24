package com.moara.moa.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TenantServiceTest {
  @Autowired private TenantService tenantService;

  @Test
  void seedsDefaultMoaTenant() {
    Tenant tenant = tenantService.getDefaultTenant();

    assertEquals(Tenant.DEFAULT_TENANT_ID, tenant.getId());
    assertEquals("MOARA", tenant.getCode());
    assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
  }

  @Test
  void createsTenantAndRejectsDuplicateCode() {
    Tenant created = tenantService.createTenant(new CreateTenantCommand("Acme Corp", "ACME"));

    assertEquals("ACME", tenantService.getByCode("ACME").getCode());
    assertEquals("Acme Corp", created.getName());

    assertThrows(
        DuplicateTenantException.class,
        () -> tenantService.createTenant(new CreateTenantCommand("Acme 2", "ACME")));
  }

  @Test
  void disablesTenant() {
    Tenant created = tenantService.createTenant(new CreateTenantCommand("Temp", "TEMP"));

    tenantService.disableTenant(created.getId());

    assertFalse(tenantService.getById(created.getId()).isActive());
  }
}
