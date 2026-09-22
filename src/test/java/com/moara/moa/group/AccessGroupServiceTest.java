package com.moara.moa.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
class AccessGroupServiceTest {
  @Autowired private AccessGroupService groupService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Test
  void createsGroupAndRejectsDuplicateNameInTenant() {
    String name = "운영팀-" + System.nanoTime();
    AccessGroup group = groupService.create(MOA, form(name));
    assertEquals(MOA, group.getTenantId());
    assertEquals(AccessGroupStatus.ACTIVE, group.getStatus());

    // 같은 테넌트 내 동일 이름(대소문자 무시) 중복 금지.
    assertThrows(DuplicateAccessGroupException.class,
        () -> groupService.create(MOA, form(name.toUpperCase())));
  }

  @Test
  void otherTenantCannotSeeGroup() {
    Tenant other = tenantService.createTenant(
        new CreateTenantCommand("Acme " + System.nanoTime(), "ACME" + System.nanoTime()));
    AccessGroup moaGroup = groupService.create(MOA, form("moa-group-" + System.nanoTime()));

    UUID otherTenant = other.getId();
    assertThrows(AccessGroupNotFoundException.class,
        () -> groupService.findById(otherTenant, moaGroup.getId()));
  }

  @Test
  void addsAndRemovesMemberIdempotently() {
    ManagedUser user = createMoaUser();
    AccessGroup group = groupService.create(MOA, form("group-" + System.nanoTime()));

    groupService.addMember(MOA, group.getId(), user.getId());
    groupService.addMember(MOA, group.getId(), user.getId()); // 멱등
    assertEquals(1, groupService.findMembers(MOA, group.getId()).size());
    assertTrue(groupService.findGroupsOfUser(MOA, user.getId()).stream()
        .anyMatch(m -> m.getGroupId().equals(group.getId())));

    groupService.removeMember(MOA, group.getId(), user.getId());
    assertEquals(0, groupService.findMembers(MOA, group.getId()).size());
  }

  @Test
  void rejectsCrossTenantMembership() {
    ManagedUser moaUser = createMoaUser();
    Tenant other = tenantService.createTenant(
        new CreateTenantCommand("Beta " + System.nanoTime(), "BETA" + System.nanoTime()));
    AccessGroup otherGroup = groupService.create(other.getId(), form("other-group-" + System.nanoTime()));

    // MOA 사용자를 다른 테넌트의 그룹에 매핑 시도 → 차단.
    UUID otherTenant = other.getId();
    UUID otherGroupId = otherGroup.getId();
    UUID moaUserId = moaUser.getId();
    assertThrows(CrossTenantMembershipException.class,
        () -> groupService.addMember(otherTenant, otherGroupId, moaUserId));
  }

  private ManagedUser createMoaUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private AccessGroupForm form(String name) {
    return new AccessGroupForm(name, "설명", AccessGroupStatus.ACTIVE, null);
  }
}
