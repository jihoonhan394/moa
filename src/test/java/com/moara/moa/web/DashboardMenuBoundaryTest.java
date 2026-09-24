package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회귀(라이브 E2E에서 발견): 인프라·자산 관리자도 /dashboard로 착지하는데, 대시보드 본문의 관리자 전용
 * 링크(사용자 관리=/users 등)가 노출돼 누르면 403이 나던 문제. 메뉴 노출 조건 = URL 인가 정책과 일치해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardMenuBoundaryTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void usersLinkOnDashboardOnlyForTenantAdmin() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("경계사", "BND" + System.nanoTime()));
    UUID tid = tenant.getId();
    ManagedUser admin = user(tid, Set.of(UserRole.TENANT_ADMIN));
    ManagedUser infra = user(tid, Set.of(UserRole.INFRA_MANAGER));
    ManagedUser asset = user(tid, Set.of(UserRole.ASSET_MANAGER));

    // 기관 관리자: 대시보드에 /users 링크 노출 + 직접 접근 200.
    mockMvc.perform(get("/dashboard").with(authentication(auth(admin))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("href=\"/users\"")));
    mockMvc.perform(get("/users").with(authentication(auth(admin)))).andExpect(status().isOk());

    // 인프라·자산 관리자: 대시보드에 /users 링크 미노출 + 직접 접근 403(메뉴=인가 일치).
    for (ManagedUser mgr : new ManagedUser[] {infra, asset}) {
      mockMvc.perform(get("/dashboard").with(authentication(auth(mgr))))
          .andExpect(status().isOk())
          .andExpect(content().string(not(containsString("href=\"/users\""))));
      mockMvc.perform(get("/users").with(authentication(auth(mgr)))).andExpect(status().isForbidden());
    }
  }

  /**
   * 회귀: {@code /expirations}는 SecurityConfig가 세 역할 모두에게 열어 두는데 사이드바는
   * <b>자산 관리자에게만</b> 보여 줬다. 인프라 관리자는 서버·접근권한 만료가 자기 소관인데도
   * 메뉴에서 만료 화면을 찾을 수 없었다. 이 파일의 규칙(메뉴 노출 = 인가 정책)을 그대로 적용한다.
   */
  @Test
  void expirationsMenuVisibleToEveryRoleAllowedToOpenIt() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("만료경계사", "EXP" + System.nanoTime()));
    UUID tid = tenant.getId();

    for (UserRole role : new UserRole[] {
        UserRole.TENANT_ADMIN, UserRole.INFRA_MANAGER, UserRole.ASSET_MANAGER}) {
      ManagedUser manager = user(tid, Set.of(role));
      mockMvc.perform(get("/dashboard").with(authentication(auth(manager))))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("href=\"/expirations\"")));
      mockMvc.perform(get("/expirations").with(authentication(auth(manager))))
          .andExpect(status().isOk());
    }
  }

  /**
   * 감사 로그와 접속 이력은 사이드바 항목 둘이 아니라 한 화면의 탭 둘이다. 어느 쪽으로 들어와도
   * 서로에게 갈 수 있어야 묶음이 성립한다 — 한쪽에서 탭이 빠지면 나머지는 도달 불가가 된다.
   */
  @Test
  void auditAndAccessHistoryLinkToEachOtherAsTabs() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("감사탭사", "AUD" + System.nanoTime()));
    ManagedUser admin = user(tenant.getId(), Set.of(UserRole.TENANT_ADMIN));

    for (String url : new String[] {"/audit", "/access-history"}) {
      mockMvc.perform(get(url).with(authentication(auth(admin))))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("class=\"subnav\"")))
          .andExpect(content().string(containsString("href=\"/audit\"")))
          .andExpect(content().string(containsString("href=\"/access-history\"")));
    }
  }

  private ManagedUser user(UUID tid, Set<UserRole> roles) {
    long n = System.nanoTime();
    String email = "bnd" + n + "@test.com";
    return userService.create(tid,
        new UserForm("경계" + n, "경계", email, "010-1234-5678", "safe-password-123", UserStatus.ACTIVE), roles);
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    var auths = user.getRoles().stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toSet());
    return new UsernamePasswordAuthenticationToken(principal, "", auths);
  }
}
