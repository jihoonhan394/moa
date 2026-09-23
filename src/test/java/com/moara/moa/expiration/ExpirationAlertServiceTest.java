package com.moara.moa.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.inventory.InventoryItemForm;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.inventory.InventoryItemType;
import com.moara.moa.notification.Notification;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 만료 임박 알림 배치: 임계일 판정·중복 차단·수신자 범위·테넌트 격리를 검증한다.
 * daysLeft는 {@link ExpirationService}가 계산하므로, 만료일은 항상
 * {@link ExpirationAlertService#ZONE} 기준 오늘(서버 기본 시간대가 아니라)로부터 상대 계산한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpirationAlertServiceTest {
  @Autowired private ExpirationAlertService alertService;
  @Autowired private InventoryItemService inventoryService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private NotificationService notificationService;

  @Test
  void sendsAlertWhenExpiryHitsThresholdDay() {
    UUID tenantId = createTenant();
    ManagedUser admin = createManager(tenantId, UserRole.TENANT_ADMIN);
    createExpiringItem(tenantId, "임계일 노트북", daysFromNow(7));

    int sent = alertService.notifyTenant(tenantId);

    assertThat(sent).isGreaterThan(0);
    List<Notification> notifications = notificationService.list(tenantId, admin.getId());
    assertThat(notifications).anyMatch(n -> n.getTitle().contains("D-7") && n.getTitle().contains("임계일 노트북"));
  }

  @Test
  void doesNotSendWhenNotThresholdDay() {
    UUID tenantId = createTenant();
    ManagedUser admin = createManager(tenantId, UserRole.TENANT_ADMIN);
    createExpiringItem(tenantId, "임계아닌날 노트북", daysFromNow(10));

    int sent = alertService.notifyTenant(tenantId);

    assertThat(sent).isZero();
    assertThat(notificationService.list(tenantId, admin.getId())).isEmpty();
  }

  @Test
  void doesNotSendForAlreadyExpiredItem() {
    UUID tenantId = createTenant();
    ManagedUser admin = createManager(tenantId, UserRole.TENANT_ADMIN);
    createExpiringItem(tenantId, "만료지난 노트북", daysFromNow(-1));

    int sent = alertService.notifyTenant(tenantId);

    assertThat(sent).isZero();
    assertThat(notificationService.list(tenantId, admin.getId())).isEmpty();
  }

  @Test
  void secondRunOnSameDaySkipsDuplicate() {
    UUID tenantId = createTenant();
    ManagedUser admin = createManager(tenantId, UserRole.TENANT_ADMIN);
    createExpiringItem(tenantId, "중복차단 노트북", daysFromNow(3));

    int firstRun = alertService.notifyTenant(tenantId);
    int secondRun = alertService.notifyTenant(tenantId);

    assertThat(firstRun).isGreaterThan(0);
    assertThat(secondRun).isZero();
    assertThat(notificationService.list(tenantId, admin.getId())).hasSize(firstRun);
  }

  @Test
  void onlyManagementRolesReceiveAlertNotPlainUser() {
    UUID tenantId = createTenant();
    ManagedUser admin = createManager(tenantId, UserRole.ASSET_MANAGER);
    ManagedUser plainUser = userService.create(tenantId, new UserForm(
        "plain" + System.nanoTime(), "일반사용자", "plain" + System.nanoTime() + "@example.com",
        "safe-password-123", UserStatus.ACTIVE));
    createExpiringItem(tenantId, "수신자범위 노트북", daysFromNow(1));

    int sent = alertService.notifyTenant(tenantId);

    assertThat(sent).isGreaterThan(0);
    assertThat(notificationService.list(tenantId, admin.getId())).isNotEmpty();
    assertThat(notificationService.list(tenantId, plainUser.getId())).isEmpty();
  }

  @Test
  void doesNotLeakAlertAcrossTenants() {
    UUID tenantAId = createTenant();
    UUID tenantBId = createTenant();
    ManagedUser adminA = createManager(tenantAId, UserRole.TENANT_ADMIN);
    ManagedUser adminB = createManager(tenantBId, UserRole.TENANT_ADMIN);
    createExpiringItem(tenantAId, "A기관 노트북", daysFromNow(0));

    int sent = alertService.notifyTenant(tenantAId);

    assertThat(sent).isGreaterThan(0);
    assertThat(notificationService.list(tenantAId, adminA.getId())).isNotEmpty();
    assertThat(notificationService.list(tenantBId, adminB.getId())).isEmpty();
  }

  private UUID createTenant() {
    String suffix = String.valueOf(System.nanoTime());
    return tenantService.createTenant(new CreateTenantCommand("만료알림기관" + suffix, "EA" + suffix)).getId();
  }

  private ManagedUser createManager(UUID tenantId, UserRole role) {
    String username = "mgr" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        username, "관리자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE), role);
  }

  private void createExpiringItem(UUID tenantId, String name, LocalDate expiresAt) {
    inventoryService.create(tenantId, new InventoryItemForm(
        name, InventoryItemType.PHYSICAL, "노트북", "SN-" + System.nanoTime(), expiresAt, null));
  }

  private LocalDate daysFromNow(long days) {
    return LocalDate.now(ExpirationAlertService.ZONE).plusDays(days);
  }
}
