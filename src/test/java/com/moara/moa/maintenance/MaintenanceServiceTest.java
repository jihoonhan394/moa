package com.moara.moa.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.notification.NotificationService;
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

/**
 * 점검창 등록 → 관련자(담당자) 인앱 알림 발행, 등록자 본인 제외, 퇴사 시 담당 해제를 검증한다.
 * (메일은 SMTP 미설정이면 조용히 넘어가므로 인앱 알림만으로 통지 보장을 확인.)
 */
@SpringBootTest
@ActiveProfiles("test")
class MaintenanceServiceTest {
  @Autowired private MaintenanceService maintenanceService;
  @Autowired private NotificationService notificationService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void windowNotifiesOwnersButNotTheActor() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("점검사", "MW" + System.nanoTime()));
    ManagedUser owner = newUser(tenant.getId(), "담당자");
    ManagedUser actor = newUser(tenant.getId(), "등록자");
    UUID assetId = UUID.randomUUID();

    // 등록자도 같은 자산의 담당자로 지정 → 등록 통지에서 스스로는 제외되어야 한다.
    maintenanceService.addOwner(tenant.getId(), MaintenanceTargetType.ASSET, assetId, owner.getId());
    maintenanceService.addOwner(tenant.getId(), MaintenanceTargetType.ASSET, assetId, actor.getId());

    maintenanceService.createWindow(
        tenant.getId(), MaintenanceTargetType.ASSET, assetId, actor.getId(),
        new MaintenanceWindowForm("펌웨어 점검", "야간 작업", LocalDateTime.now().plusDays(1), null));

    assertThat(notificationService.unreadCount(tenant.getId(), owner.getId())).isEqualTo(1);
    assertThat(notificationService.unreadCount(tenant.getId(), actor.getId())).isZero();
  }

  @Test
  void addOwnerIsIdempotentAndOffboardRemovesOwnerships() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("해제사", "MO" + System.nanoTime()));
    ManagedUser user = newUser(tenant.getId(), "담당자");
    UUID assetId = UUID.randomUUID();

    maintenanceService.addOwner(tenant.getId(), MaintenanceTargetType.ASSET, assetId, user.getId());
    maintenanceService.addOwner(tenant.getId(), MaintenanceTargetType.ASSET, assetId, user.getId()); // 중복 무시
    assertThat(maintenanceService.owners(tenant.getId())).hasSize(1);

    long removed = maintenanceService.removeOwnershipsOf(tenant.getId(), user.getId());
    assertThat(removed).isEqualTo(1);
    assertThat(maintenanceService.owners(tenant.getId())).isEmpty();
  }

  private ManagedUser newUser(UUID tenantId, String name) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, name, username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
