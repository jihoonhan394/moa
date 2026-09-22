package com.moara.moa.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PermissionSetServiceTest {
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @Autowired private PermissionSetService service;
  @Autowired private AccessGroupService groupService;
  @Autowired private AssetService assetService;
  @Autowired private ManagedUserService userService;

  @Test
  void createRejectsDuplicateName() {
    PermissionForm form = new PermissionForm("perm-" + System.nanoTime(), "desc", PermissionStatus.ACTIVE);
    service.create(TENANT, form);
    assertThrows(DuplicatePermissionException.class, () -> service.create(TENANT, form));
  }

  @Test
  void addEntryIsIdempotentAndListed() {
    Permission permission = newPermission();
    Asset asset = newServer();
    service.addEntry(TENANT, permission.getId(), asset.getId(), PermissionProtocol.SSH);
    service.addEntry(TENANT, permission.getId(), asset.getId(), PermissionProtocol.SSH);
    List<PermissionEntry> entries = service.findEntries(TENANT, permission.getId());
    assertEquals(1, entries.size());
    assertEquals(PermissionProtocol.SSH, entries.get(0).getAction());
  }

  @Test
  void assignToGroupIsIdempotent() {
    Permission permission = newPermission();
    AccessGroup group = newGroup();
    service.assignToGroup(TENANT, permission.getId(), group.getId());
    service.assignToGroup(TENANT, permission.getId(), group.getId());
    assertEquals(1, service.findGroupAssignments(TENANT, permission.getId()).size());
  }

  @Test
  void assignToUserUpsertsExpiry() {
    Permission permission = newPermission();
    ManagedUser user = newUser();
    service.assignToUser(TENANT, permission.getId(), user.getId(), OffsetDateTime.now().plusHours(1));
    service.assignToUser(TENANT, permission.getId(), user.getId(), OffsetDateTime.now().plusDays(1));
    List<PermissionUserAssignment> assignments = service.findUserAssignments(TENANT, permission.getId());
    assertEquals(1, assignments.size());
    assertTrue(assignments.get(0).isActiveAt(OffsetDateTime.now()));
    assertTrue(assignments.get(0).getExpiresAt().isAfter(OffsetDateTime.now().plusHours(2)));
  }

  @Test
  void unassignUserRemovesIt() {
    Permission permission = newPermission();
    ManagedUser user = newUser();
    service.assignToUser(TENANT, permission.getId(), user.getId(), null);
    assertEquals(1, service.findUserAssignments(TENANT, permission.getId()).size());
    service.unassignUser(TENANT, permission.getId(), user.getId());
    assertTrue(service.findUserAssignments(TENANT, permission.getId()).isEmpty());
  }

  private Permission newPermission() {
    return service.create(TENANT, new PermissionForm("perm-" + System.nanoTime(), null, PermissionStatus.ACTIVE));
  }

  private AccessGroup newGroup() {
    return groupService.create(TENANT, new AccessGroupForm("grp-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
  }

  private Asset newServer() {
    return assetService.create(TENANT, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.90", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
  }

  private ManagedUser newUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
