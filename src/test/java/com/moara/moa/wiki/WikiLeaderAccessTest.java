package com.moara.moa.wiki;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 부서장 위키 공간 승격(계산형): 그룹에 열람(VIEW) 권한이 부여된 공간에서, 그 그룹의 부서장은 MANAGE로
 * 승격되고 일반 팀원은 VIEW에 머문다. 저장 없이 접근 판정 시점에 계산된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class WikiLeaderAccessTest {
  @Autowired private WikiAccessService accessService;
  @Autowired private WikiSpaceService spaceService;
  @Autowired private AccessGroupService groupService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void leaderGetsManageOnDepartmentSpace() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("위키부서", "WL" + System.nanoTime()));
    UUID t = tenant.getId();
    ManagedUser leader = newUser(t);
    ManagedUser member = newUser(t);
    AccessGroup group = groupService.create(t, new AccessGroupForm("영업팀", null, AccessGroupStatus.ACTIVE, null));
    groupService.addMember(t, group.getId(), leader.getId());
    groupService.addMember(t, group.getId(), member.getId());
    groupService.setLeader(t, group.getId(), leader.getId(), true);

    WikiSpace space = spaceService.create(t, null, new WikiSpaceForm("영업 문서", "영업"));
    spaceService.grant(t, space.getId(), WikiSubjectType.GROUP, group.getId(), WikiAccessLevel.VIEW);

    // 일반 팀원 = 열람, 부서장 = 관리 승격.
    assertThat(accessService.effectiveLevel(t, member.getId(), false, space.getId()))
        .isEqualTo(WikiAccessLevel.VIEW);
    assertThat(accessService.effectiveLevel(t, leader.getId(), false, space.getId()))
        .isEqualTo(WikiAccessLevel.MANAGE);
  }

  private ManagedUser newUser(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "사원", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
