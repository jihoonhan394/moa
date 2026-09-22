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

/** 그룹 멤버의 부서장 지정/해제를 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class GroupLeaderTest {
  @Autowired private AccessGroupService groupService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void designatesAndClearsDepartmentLeader() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("부서사", "GL" + System.nanoTime()));
    ManagedUser user = newUser(tenant.getId());
    AccessGroup group = groupService.create(tenant.getId(), new AccessGroupForm("영업팀", "영업", null, null));
    groupService.addMember(tenant.getId(), group.getId(), user.getId());

    // 기본은 부서장 아님.
    assertThat(leaderFlag(tenant.getId(), group.getId(), user.getId())).isFalse();

    // 지정 → 부서장.
    groupService.setLeader(tenant.getId(), group.getId(), user.getId(), true);
    assertThat(leaderFlag(tenant.getId(), group.getId(), user.getId())).isTrue();

    // 해제 → 부서장 아님.
    groupService.setLeader(tenant.getId(), group.getId(), user.getId(), false);
    assertThat(leaderFlag(tenant.getId(), group.getId(), user.getId())).isFalse();
  }

  private boolean leaderFlag(UUID tenantId, UUID groupId, UUID userId) {
    return groupService.findMembers(tenantId, groupId).stream()
        .filter(m -> m.getUserId().equals(userId))
        .findFirst().orElseThrow().isLeader();
  }

  private ManagedUser newUser(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "사원", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
