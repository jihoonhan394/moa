package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 부서장 위임(서버·솔루션): 팀 소유만 CRUD, 봉쇄(솔루션은 팀 서버에만) 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TeamServerSolutionFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;
  @Autowired private AssetService assetService;
  @Autowired private ManagedSolutionService solutionService;

  @Test
  void leaderCreatesTeamServerAndCannotDeleteForeign() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("팀사", "TS" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    ManagedUser leader = user(tenantId);
    AccessGroup mine = group(tenantId);
    AccessGroup others = group(tenantId);
    groupService.addMember(tenantId, mine.getId(), leader.getId());
    groupService.setLeader(tenantId, mine.getId(), leader.getId(), true);
    var authn = auth(leader);

    String name = "빌드서버-" + System.nanoTime();
    mockMvc.perform(post("/team/servers").with(authentication(authn)).with(csrf())
            .param("name", name).param("protocol", "SSH").param("host", "10.0.0.30").param("port", "22")
            .param("ownerGroupId", mine.getId().toString()))
        .andExpect(status().is3xxRedirection());
    Asset created = assetService.findAll(tenantId).stream()
        .filter(a -> a.getName().equals(name)).findFirst().orElseThrow();
    Assertions.assertThat(created.getOwnerGroupId()).isEqualTo(mine.getId());

    // 남의 부서 소유 서버는 삭제 불가.
    Asset foreign = assetService.create(tenantId, new AssetForm(
        "남의서버-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "10.0.0.99", 22, "",
        "LINUX", "", AssetStatus.ACTIVE));
    assetService.assignOwnerGroup(tenantId, foreign.getId(), others.getId());
    mockMvc.perform(post("/team/servers/" + foreign.getId() + "/delete").with(authentication(authn)).with(csrf()))
        .andExpect(status().is3xxRedirection());
    Assertions.assertThat(assetService.findAll(tenantId)).anyMatch(a -> a.getId().equals(foreign.getId()));
  }

  @Test
  void solutionOnlyAllowedOnTeamOwnedServer() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("팀사", "TS" + System.nanoTime()));
    UUID tenantId = tenant.getId();
    ManagedUser leader = user(tenantId);
    AccessGroup mine = group(tenantId);
    groupService.addMember(tenantId, mine.getId(), leader.getId());
    groupService.setLeader(tenantId, mine.getId(), leader.getId(), true);
    var authn = auth(leader);

    // 팀 소유 서버.
    Asset teamServer = assetService.create(tenantId, new AssetForm(
        "팀서버-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "10.0.1.10", 22, "",
        "LINUX", "", AssetStatus.ACTIVE));
    assetService.assignOwnerGroup(tenantId, teamServer.getId(), mine.getId());
    // 팀 소유 아닌 서버.
    Asset otherServer = assetService.create(tenantId, new AssetForm(
        "외부서버-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "10.0.2.10", 22, "",
        "LINUX", "", AssetStatus.ACTIVE));

    // 팀 서버에 등록 → 성공.
    String ok = "팀솔루션-" + System.nanoTime();
    mockMvc.perform(post("/team/solutions").with(authentication(authn)).with(csrf())
            .param("assetId", teamServer.getId().toString()).param("name", ok)
            .param("type", "LINUX_DAEMON").param("identifier", "svc.a")
            .param("healthCheckType", "NONE").param("status", "ACTIVE")
            .param("controlProtocol", "SSH").param("ownerGroupId", mine.getId().toString()))
        .andExpect(status().is3xxRedirection());
    Assertions.assertThat(solutionService.findAll(tenantId)).anyMatch(s -> s.getName().equals(ok));

    // 팀 소유 아닌 서버에 등록 → 봉쇄(차단).
    String bad = "탈출솔루션-" + System.nanoTime();
    mockMvc.perform(post("/team/solutions").with(authentication(authn)).with(csrf())
            .param("assetId", otherServer.getId().toString()).param("name", bad)
            .param("type", "LINUX_DAEMON").param("identifier", "svc.b")
            .param("healthCheckType", "NONE").param("status", "ACTIVE")
            .param("controlProtocol", "SSH").param("ownerGroupId", mine.getId().toString()))
        .andExpect(status().is3xxRedirection());
    Assertions.assertThat(solutionService.findAll(tenantId)).noneMatch(s -> s.getName().equals(bad));
  }

  private ManagedUser user(UUID tenantId) {
    String username = "lead" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "부서장", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private AccessGroup group(UUID tenantId) {
    return groupService.create(tenantId,
        new AccessGroupForm("팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(
        principal, "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
  }
}
