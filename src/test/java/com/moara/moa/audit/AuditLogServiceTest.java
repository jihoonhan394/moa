package com.moara.moa.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AuditLogServiceTest {
  @Autowired private AuditLogService auditLogService;
  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;

  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Test
  void recordsTenantActionAndQueriesByTenant() {
    UUID actor = createMoaUser();
    UUID targetUser = createMoaUser();

    AuditLog log = auditLogService.recordTenantAction(
        MOA, actor, "USER_DISABLE", "ManagedUser", targetUser, AuditResult.SUCCESS, "disabled by admin");

    assertEquals(AuditActionScope.TENANT, log.getActionScope());
    assertEquals(MOA, log.getTenantId());
    assertTrue(auditLogService.findByTenant(MOA).stream().anyMatch(a -> a.getId().equals(log.getId())));
    assertTrue(auditLogService.findByActor(actor).stream().anyMatch(a -> a.getId().equals(log.getId())));
  }

  @Test
  void recordsGlobalActionWithoutTenant() {
    UUID actor = createMoaUser();

    AuditLog log = auditLogService.recordGlobalAction(
        actor, null, "TENANT_CREATE", "Tenant", UUID.randomUUID(), AuditResult.SUCCESS, "new tenant");

    assertEquals(AuditActionScope.GLOBAL, log.getActionScope());
    assertNull(log.getTenantId());
    assertTrue(auditLogService.findGlobal().stream().anyMatch(a -> a.getId().equals(log.getId())));
  }

  @Test
  void tenantQueryIsIsolated() {
    UUID actor = createMoaUser();
    AuditLog moaLog = auditLogService.recordTenantAction(
        MOA, actor, "GROUP_CREATE", "AccessGroup", UUID.randomUUID(), AuditResult.SUCCESS, null);
    Tenant other = tenantService.createTenant(
        new CreateTenantCommand("Other " + System.nanoTime(), "OTH" + System.nanoTime()));

    // 다른 테넌트 조회에는 MOA 로그가 포함되지 않는다.
    assertFalse(auditLogService.findByTenant(other.getId()).stream()
        .anyMatch(a -> a.getId().equals(moaLog.getId())));
  }

  private UUID createMoaUser() {
    String username = "user" + System.nanoTime();
    ManagedUser user = userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
    return user.getId();
  }
}
