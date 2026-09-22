package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 부서장 역할 위임: (1) 자기가 보유한 역할만 팀원에게 위임 가능, (2) 보유하지 않은 역할·팀 밖 사용자엔 거부.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamDelegationFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;

  @Test
  void leaderDelegatesOnlyHeldRolesToTeam() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("위임회사", "TD" + System.nanoTime()));
    UUID t = tenant.getId();
    // 경영지원 부서장: 자산 관리자 역할 보유 + 그룹 부서장.
    ManagedUser leader = userService.create(t, form("부서장"), Set.of(UserRole.ASSET_MANAGER));
    ManagedUser member = userService.create(t, form("대리"), Set.of(UserRole.USER));
    ManagedUser outsider = userService.create(t, form("타부서"), Set.of(UserRole.USER));
    AccessGroup group = groupService.create(t, new AccessGroupForm("경영지원", null, AccessGroupStatus.ACTIVE, null));
    groupService.addMember(t, group.getId(), leader.getId());
    groupService.addMember(t, group.getId(), member.getId());
    groupService.setLeader(t, group.getId(), leader.getId(), true);

    var auth = new UsernamePasswordAuthenticationToken(
        new MoaUserDetails(userService.findById(leader.getId())), "",
        new MoaUserDetails(userService.findById(leader.getId())).getAuthorities());

    // 보유 역할(자산 관리자)을 팀원에게 위임 → 성공.
    mockMvc.perform(post("/team/roles")
            .param("userId", member.getId().toString()).param("role", "ASSET_MANAGER").param("grant", "true")
            .with(authentication(auth)).with(csrf()))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
    Assertions.assertTrue(userService.findById(member.getId()).hasRole(UserRole.ASSET_MANAGER));

    // 미보유 역할(인프라 관리자)은 위임 거부 → 부여되지 않음.
    mockMvc.perform(post("/team/roles")
            .param("userId", member.getId().toString()).param("role", "INFRA_MANAGER").param("grant", "true")
            .with(authentication(auth)).with(csrf()))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
    Assertions.assertFalse(userService.findById(member.getId()).hasRole(UserRole.INFRA_MANAGER));

    // 팀 밖 사용자엔 위임 거부.
    mockMvc.perform(post("/team/roles")
            .param("userId", outsider.getId().toString()).param("role", "ASSET_MANAGER").param("grant", "true")
            .with(authentication(auth)).with(csrf()))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
    Assertions.assertFalse(userService.findById(outsider.getId()).hasRole(UserRole.ASSET_MANAGER));
  }

  private UserForm form(String name) {
    String username = "u" + System.nanoTime();
    return new UserForm(username, name, username + "@example.com", "safe-password-123", UserStatus.ACTIVE);
  }
}
