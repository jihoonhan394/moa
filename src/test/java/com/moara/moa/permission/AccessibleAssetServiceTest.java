package com.moara.moa.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 묶음 권한 모델(T18) 기준 접근 판정 검증: 그룹 부착(멤버십) + 사용자 직접 부착(만료). */
@SpringBootTest
@ActiveProfiles("test")
class AccessibleAssetServiceTest {
  @Autowired private AccessibleAssetService accessibleAssetService;
  @Autowired private AccessGroupService groupService;
  @Autowired private AssetService assetService;
  @Autowired private PermissionSetService permissionSetService;
  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;

  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Test
  void listsAssetAccessibleThroughGroupMembership() {
    Fixture f = grantedFixture();

    assertTrue(accessibleAssetService.findAccessibleAssets(MOA, f.userId).stream()
        .anyMatch(a -> a.getId().equals(f.assetId)));
    assertTrue(accessibleAssetService.canAccess(MOA, f.userId, f.assetId));
  }

  @Test
  void excludesAssetWhenUserIsNotGroupMember() {
    ManagedUser user = createMoaUser();
    AccessGroup group = groupService.create(MOA, groupForm());
    Asset asset = assetService.create(MOA, assetForm(AssetStatus.ACTIVE));
    Permission permission = permissionSetService.create(MOA, permForm());
    permissionSetService.addEntry(MOA, permission.getId(), asset.getId(), PermissionProtocol.SSH);
    permissionSetService.assignToGroup(MOA, permission.getId(), group.getId());
    // 멤버십을 추가하지 않음 → 접근 불가

    assertFalse(accessibleAssetService.canAccess(MOA, user.getId(), asset.getId()));
  }

  @Test
  void excludesWhenGroupDisabled() {
    Fixture f = grantedFixture();
    groupService.disable(MOA, f.groupId);

    assertFalse(accessibleAssetService.canAccess(MOA, f.userId, f.assetId));
  }

  @Test
  void excludesWhenAssetDisabled() {
    Fixture f = grantedFixture();
    assetService.update(MOA, f.assetId, assetForm(AssetStatus.DISABLED));

    assertFalse(accessibleAssetService.canAccess(MOA, f.userId, f.assetId));
  }

  @Test
  void grantsViaNonExpiredDirectUserAssignment() {
    ManagedUser user = createMoaUser();
    Asset asset = assetService.create(MOA, assetForm(AssetStatus.ACTIVE));
    Permission permission = permissionSetService.create(MOA, permForm());
    permissionSetService.addEntry(MOA, permission.getId(), asset.getId(), PermissionProtocol.SSH);
    permissionSetService.assignToUser(MOA, permission.getId(), user.getId(), null); // 무기한

    assertTrue(accessibleAssetService.canAccess(MOA, user.getId(), asset.getId()));
  }

  @Test
  void excludesExpiredDirectUserAssignment() {
    ManagedUser user = createMoaUser();
    Asset asset = assetService.create(MOA, assetForm(AssetStatus.ACTIVE));
    Permission permission = permissionSetService.create(MOA, permForm());
    permissionSetService.addEntry(MOA, permission.getId(), asset.getId(), PermissionProtocol.SSH);
    permissionSetService.assignToUser(MOA, permission.getId(), user.getId(), OffsetDateTime.now().minusMinutes(1));

    assertFalse(accessibleAssetService.canAccess(MOA, user.getId(), asset.getId()));
  }

  @Test
  void isTenantScoped() {
    Fixture f = grantedFixture();
    Tenant other = tenantService.createTenant(
        new CreateTenantCommand("Other " + System.nanoTime(), "OTH" + System.nanoTime()));

    // 다른 테넌트 스코프로는 MOA 자산이 보이지 않는다.
    assertFalse(accessibleAssetService.findAccessibleAssets(other.getId(), f.userId).stream()
        .anyMatch(a -> a.getId().equals(f.assetId)));
    assertFalse(accessibleAssetService.canAccess(other.getId(), f.userId, f.assetId));
  }

  /** 사용자·그룹·자산·권한(엔트리)·그룹부착·멤버십이 모두 연결된 접근 가능 상태를 만든다. */
  private Fixture grantedFixture() {
    ManagedUser user = createMoaUser();
    AccessGroup group = groupService.create(MOA, groupForm());
    Asset asset = assetService.create(MOA, assetForm(AssetStatus.ACTIVE));
    Permission permission = permissionSetService.create(MOA, permForm());
    permissionSetService.addEntry(MOA, permission.getId(), asset.getId(), PermissionProtocol.SSH);
    permissionSetService.assignToGroup(MOA, permission.getId(), group.getId());
    groupService.addMember(MOA, group.getId(), user.getId());
    return new Fixture(user.getId(), group.getId(), asset.getId());
  }

  private record Fixture(UUID userId, UUID groupId, UUID assetId) {}

  private ManagedUser createMoaUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private AccessGroupForm groupForm() {
    return new AccessGroupForm("group-" + System.nanoTime(), "설명", AccessGroupStatus.ACTIVE, null);
  }

  private PermissionForm permForm() {
    return new PermissionForm("perm-" + System.nanoTime(), null, PermissionStatus.ACTIVE);
  }

  private AssetForm assetForm(AssetStatus status) {
    return new AssetForm("asset-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH,
        "192.0.2.10", 22, "", "LINUX", "test", status);
  }
}
