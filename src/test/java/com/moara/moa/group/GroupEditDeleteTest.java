package com.moara.moa.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** 그룹 수정(이름)과 삭제(하위는 상위로 승격, 멤버십 제거)를 검증. */
@SpringBootTest
@ActiveProfiles("test")
class GroupEditDeleteTest {
  @Autowired private AccessGroupService groupService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void renameAndDeleteReparentsChildrenAndRemovesMembers() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("조직사", "GE" + System.nanoTime()));
    UUID t = tenant.getId();
    AccessGroup hq = groupService.create(t, form("본부", null));
    AccessGroup team = groupService.create(t, form("영업팀", hq.getId()));
    AccessGroup cell = groupService.create(t, form("영업1셀", team.getId()));
    ManagedUser member = newUser(t);
    groupService.addMember(t, team.getId(), member.getId());
    groupService.setLeader(t, team.getId(), member.getId(), true);

    // 이름 수정.
    groupService.update(t, hq.getId(), new AccessGroupForm("본사", "설명", AccessGroupStatus.ACTIVE, null));
    assertThat(groupService.findById(t, hq.getId()).getName()).isEqualTo("본사");

    // 삭제: 영업팀 → 하위(영업1셀)는 상위(본사)로 승격, 멤버십 제거.
    groupService.delete(t, team.getId());
    assertThatThrownBy(() -> groupService.findById(t, team.getId()))
        .isInstanceOf(AccessGroupNotFoundException.class);
    assertThat(groupService.findById(t, cell.getId()).getParentId()).isEqualTo(hq.getId());
    assertThat(groupService.findGroupsOfUser(t, member.getId())).isEmpty();
  }

  private AccessGroupForm form(String name, UUID parentId) {
    return new AccessGroupForm(name, null, AccessGroupStatus.ACTIVE, parentId);
  }

  private ManagedUser newUser(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "사원", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
