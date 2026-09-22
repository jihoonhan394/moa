package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.AccessGroupStatus;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.FeatureModule;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 시나리오 워크스루(서버측 진단) — 기능 전부 켠 기관에 역할별 계정을 만들고, 각 역할이 보는 화면을 실제로
 * 렌더링해 <b>깨지는 페이지(500/예상외 403)를 한 번에 모아</b> 보고한다. 브라우저 없이 템플릿·접근규칙 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScenarioWalkthroughTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private AccessGroupService groupService;

  @Test
  void everyRoleRendersItsPagesWithoutError() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("워크스루사", "WALK" + System.nanoTime()));
    tenantService.updateDetail(tenant.getId(), null, null, EnumSet.allOf(FeatureModule.class));
    UUID tid = tenant.getId();

    ManagedUser admin = user(tid, "김관리", Set.of(UserRole.TENANT_ADMIN));
    ManagedUser infra = user(tid, "이인프라", Set.of(UserRole.INFRA_MANAGER));
    ManagedUser asset = user(tid, "박자산", Set.of(UserRole.ASSET_MANAGER));
    ManagedUser normal = user(tid, "정사원", Set.of(UserRole.USER));
    ManagedUser leader = user(tid, "최부서장", Set.of(UserRole.USER));
    AccessGroup dev = groupService.create(tid,
        new AccessGroupForm("개발팀-" + System.nanoTime(), null, AccessGroupStatus.ACTIVE, null));
    groupService.addMember(tid, dev.getId(), leader.getId());
    groupService.setLeader(tid, dev.getId(), leader.getId(), true);

    List<String> failures = new ArrayList<>();
    // 관리 콘솔
    hit(failures, admin, "/dashboard", "/users", "/invitations", "/groups", "/team",
        "/notices", "/mail", "/ai-settings", "/audit", "/access-history", "/access-review", "/security/2fa");
    // 인프라 관리자
    hit(failures, infra, "/dashboard", "/assets", "/servers", "/server-status", "/solutions",
        "/solution-sequences", "/maintenance", "/credentials", "/permissions", "/onboarding", "/expirations", "/assets/new", "/servers/new");
    // 자산 관리자
    hit(failures, asset, "/dashboard", "/inventory", "/inventory/import", "/shared-resources",
        "/expirations", "/onboarding");
    // 일반 사용자
    hit(failures, normal, "/my/workspace", "/portal", "/my-solutions", "/notices", "/security/2fa");
    // 부서장 위임 콘솔
    hit(failures, leader, "/team", "/team/servers", "/team/solutions", "/team/inventory",
        "/team/deputies", "/my/workspace");

    Assertions.assertThat(failures)
        .withFailMessage("깨진 페이지:%n%s", String.join("%n", failures))
        .isEmpty();
  }

  /** 각 경로를 그 사용자로 GET. 200이 아니면(리다이렉트 제외) 실패 목록에 상태와 함께 기록. */
  private void hit(List<String> failures, ManagedUser user, String... paths) throws Exception {
    var authn = auth(user);
    for (String path : paths) {
      MvcResult res = mockMvc.perform(get(path).with(authentication(authn))).andReturn();
      int status = res.getResponse().getStatus();
      if (status != 200) {
        String msg = res.getResolvedException() != null ? " - " + res.getResolvedException() : "";
        failures.add(user.getName() + " " + path + " → " + status + msg);
      }
    }
  }

  private ManagedUser user(UUID tid, String name, Set<UserRole> roles) {
    String email = "walk" + System.nanoTime() + "@test.com";
    return userService.create(tid,
        new UserForm(name, name, email, "010-1234-5678", "safe-password-123", UserStatus.ACTIVE), roles);
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    var auths = user.getRoles().stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toSet());
    return new UsernamePasswordAuthenticationToken(principal, "", auths);
  }
}
