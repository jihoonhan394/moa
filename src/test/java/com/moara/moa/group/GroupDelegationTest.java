package com.moara.moa.group;

import static org.assertj.core.api.Assertions.assertThat;

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

/** 부서장 위임 판정: 부서장은 자기 부서 팀원에 대해서만 위임 권한을 갖는다(다른 부서 사용자엔 없음). */
@SpringBootTest
@ActiveProfiles("test")
class GroupDelegationTest {
  @Autowired private AccessGroupService groupService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void leaderHasAuthorityOnlyOverOwnTeam() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("위임사", "DG" + System.nanoTime()));
    UUID t = tenant.getId();
    ManagedUser leader = newUser(t, "부서장");
    ManagedUser teammate = newUser(t, "팀원");
    ManagedUser outsider = newUser(t, "타부서");

    AccessGroup sales = groupService.create(t, new AccessGroupForm("영업팀", null, AccessGroupStatus.ACTIVE, null));
    AccessGroup other = groupService.create(t, new AccessGroupForm("개발팀", null, AccessGroupStatus.ACTIVE, null));
    groupService.addMember(t, sales.getId(), leader.getId());
    groupService.addMember(t, sales.getId(), teammate.getId());
    groupService.addMember(t, other.getId(), outsider.getId());
    groupService.setLeader(t, sales.getId(), leader.getId(), true);

    assertThat(groupService.isDepartmentLeader(t, leader.getId())).isTrue();
    assertThat(groupService.isDepartmentLeader(t, teammate.getId())).isFalse();

    // 팀원엔 위임 권한 O, 타부서 사용자엔 X. 팀원 목록엔 본인 제외.
    assertThat(groupService.leads(t, leader.getId(), teammate.getId())).isTrue();
    assertThat(groupService.leads(t, leader.getId(), outsider.getId())).isFalse();
    assertThat(groupService.teamMemberIds(t, leader.getId()))
        .contains(teammate.getId()).doesNotContain(leader.getId(), outsider.getId());
  }

  private ManagedUser newUser(UUID tenantId, String name) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, name, username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
