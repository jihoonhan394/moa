package com.moara.moa.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 솔루션 운영 콘솔이 재사용하는 '대상별 담당자·점검 이력' 조회가 대상으로 정확히 스코프되는지 검증. */
@SpringBootTest
@ActiveProfiles("test")
class MaintenanceTargetQueryTest {
  @Autowired private MaintenanceService maintenanceService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void ownersAndHistoryAreScopedToTarget() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("콘솔사", "SC" + System.nanoTime()));
    UUID t = tenant.getId();
    ManagedUser owner = newUser(t);
    UUID sol = UUID.randomUUID();
    UUID otherSol = UUID.randomUUID();

    maintenanceService.addOwner(t, MaintenanceTargetType.SOLUTION, sol, owner.getId());
    maintenanceService.createWindow(t, MaintenanceTargetType.SOLUTION, sol, null,
        new MaintenanceWindowForm("패치 작업", "정기 패치", LocalDateTime.now().plusDays(1), null));

    // 해당 솔루션엔 담당자·이력 1건씩, 다른 솔루션엔 없음.
    assertThat(maintenanceService.ownersFor(t, MaintenanceTargetType.SOLUTION, sol)).hasSize(1);
    assertThat(maintenanceService.windowsFor(t, MaintenanceTargetType.SOLUTION, sol)).hasSize(1);
    assertThat(maintenanceService.ownersFor(t, MaintenanceTargetType.SOLUTION, otherSol)).isEmpty();
    assertThat(maintenanceService.windowsFor(t, MaintenanceTargetType.SOLUTION, otherSol)).isEmpty();
  }

  private ManagedUser newUser(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "담당", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
