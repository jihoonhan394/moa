package com.moara.moa.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@link SecurityConfig}의 라우트 → 역할 표를 통째로 확인한다.
 *
 * <p>여기가 <b>실질 방어선</b>이다. 메뉴를 숨겨도 주소를 직접 치면 들어오므로, 새 화면을
 * 추가하고 이 표에 줄을 안 넣으면 {@code anyRequest().authenticated()}로 떨어져
 * <b>로그인한 아무나</b> 열 수 있다. 그 실수는 화면만 봐서는 드러나지 않는다.
 *
 * <p>여긴 인가만 본다 — 허용은 "403이 아님"으로 확인한다. 허용된 뒤의 응답이 200인지 302인지
 * 404인지는 컨트롤러의 몫이고, 그것까지 묶으면 화면이 바뀔 때마다 이 표가 깨진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRouteMatrixTest {

  /** 인프라 관리자 전용(서버·솔루션·자격증명·권한). */
  private static final String[] INFRA_ROUTES = {
    "/assets", "/servers", "/server-status", "/solutions", "/solution-sequences",
    "/credentials", "/permissions", "/maintenance", "/operations",
  };

  /** 자산 관리자 전용(실물·SW 인벤토리·공유자산·소모품). */
  private static final String[] ASSET_ROUTES = {
    "/inventory", "/shared-resources", "/consumables",
  };

  /** 기관 관리자 전용(사람·조직·거버넌스·기관 설정). */
  private static final String[] TENANT_ADMIN_ROUTES = {
    "/users", "/groups", "/audit", "/access-review", "/invitations",
    "/mail", "/access-history", "/ai-settings", "/notices/new",
  };

  /** 자원을 아는 관리자(인프라·자산)가 함께 쓰는 라우트. */
  private static final String[] RESOURCE_ADMIN_ROUTES = {"/onboarding"};

  /** 관리 권한이면 누구나 보는 만료 대시보드. */
  private static final String[] ANY_MANAGER_ROUTES = {"/expirations"};

  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;

  /**
   * 권한 없는 일반 사용자는 관리 라우트 전부에서 막힌다. 새 관리 화면을 추가하고 이 배열에
   * 넣어 두면, {@code SecurityConfig}에 규칙을 빠뜨렸을 때 이 테스트가 먼저 잡는다.
   */
  @Test
  void plainUserIsDeniedEveryManagementRoute() throws Exception {
    ManagedUser user = user();
    for (String path : all()) {
      deny(path, user);
    }
  }

  /** 로그인하지 않았으면 관리 라우트에서 본문을 받지 못한다(진입 화면으로 돌려보낸다). */
  @Test
  void anonymousIsSentToEntry() throws Exception {
    for (String path : all()) {
      mockMvc.perform(get(path)).andExpect(status().is3xxRedirection());
    }
  }

  /** 인프라 관리자는 자원 화면을 열고, 사람·조직 화면과 자산 대장에서는 막힌다. */
  @Test
  void infraManagerSeesInfraOnly() throws Exception {
    ManagedUser manager = withRole(UserRole.INFRA_MANAGER);
    for (String path : INFRA_ROUTES) {
      allow(path, manager);
    }
    for (String path : TENANT_ADMIN_ROUTES) {
      deny(path, manager);
    }
    for (String path : ASSET_ROUTES) {
      deny(path, manager);
    }
  }

  /** 자산 관리자는 대장·소모품을 열고, 인프라 자원과 거버넌스에서는 막힌다. */
  @Test
  void assetManagerSeesAssetOnly() throws Exception {
    ManagedUser manager = withRole(UserRole.ASSET_MANAGER);
    for (String path : ASSET_ROUTES) {
      allow(path, manager);
    }
    for (String path : INFRA_ROUTES) {
      deny(path, manager);
    }
    for (String path : TENANT_ADMIN_ROUTES) {
      deny(path, manager);
    }
  }

  /** 기관 관리자는 거버넌스를 열고, 자원 화면은 자원 관리자 몫이라 막힌다. */
  @Test
  void tenantAdminSeesGovernanceOnly() throws Exception {
    ManagedUser admin = withRole(UserRole.TENANT_ADMIN);
    for (String path : TENANT_ADMIN_ROUTES) {
      allow(path, admin);
    }
    for (String path : INFRA_ROUTES) {
      deny(path, admin);
    }
    for (String path : ASSET_ROUTES) {
      deny(path, admin);
    }
  }

  /** 만료 대시보드는 관리 권한 세 종류가 모두 본다. 온보딩 구성은 자원 관리자 둘만 본다. */
  @Test
  void sharedAdminRoutesAreSharedExactly() throws Exception {
    for (UserRole role :
        new UserRole[] {UserRole.TENANT_ADMIN, UserRole.INFRA_MANAGER, UserRole.ASSET_MANAGER}) {
      ManagedUser manager = withRole(role);
      for (String path : ANY_MANAGER_ROUTES) {
        allow(path, manager);
      }
    }

    for (String path : RESOURCE_ADMIN_ROUTES) {
      allow(path, withRole(UserRole.INFRA_MANAGER));
      allow(path, withRole(UserRole.ASSET_MANAGER));
      deny(path, withRole(UserRole.TENANT_ADMIN));
      deny(path, user());
    }
  }

  /**
   * 플랫폼 콘솔은 SYSTEM_ADMIN 전용이다. 기관 관리자가 들어갈 수 있으면 한 기관의 관리자가
   * 다른 기관을 보게 된다 — 멀티테넌트 격리가 깨지는 지점이다.
   */
  @Test
  void platformConsoleIsSystemAdminOnly() throws Exception {
    for (String path : new String[] {"/admin", "/admin/tenants", "/admin/operators"}) {
      deny(path, withRole(UserRole.TENANT_ADMIN));
      deny(path, withRole(UserRole.INFRA_MANAGER));
      deny(path, user());
    }
  }

  /**
   * 컨트롤러가 없는 {@code /operations/**}에도 규칙이 남아 있다. 권한 없는 역할은 404가 아니라
   * <b>403</b>을 받아야 한다 — 규칙을 지우면 같은 경로가 나중에 생길 때 "로그인한 아무나"로
   * 권한이 낮아진다. 이 테스트가 그 규칙을 지킨다.
   */
  @Test
  void ruleForUnusedPathStillFailsClosed() throws Exception {
    deny("/operations", user());
    deny("/operations/anything", user());
  }

  /** 진입·정적 자원은 로그인 없이 열려야 한다(잠그면 로그인 화면이 스타일 없이 뜬다). */
  @Test
  void entryAndStaticResourcesArePublic() throws Exception {
    mockMvc.perform(get("/enter")).andExpect(status().isOk());
    mockMvc.perform(get("/css/style.css")).andExpect(status().isOk());
  }

  // ── 도우미 ────────────────────────────────────────────────────────────────

  /** 인가 통과 = 403이 아니다. 그 뒤 200/302/404는 컨트롤러의 몫이다. */
  private void allow(String path, ManagedUser as) throws Exception {
    assertNotEquals(403, statusOf(path, as), path + " 이(가) " + roleOf(as) + " 에게 막혔다");
  }

  private void deny(String path, ManagedUser as) throws Exception {
    assertEquals(403, statusOf(path, as), path + " 이(가) " + roleOf(as) + " 에게 열렸다");
  }

  private int statusOf(String path, ManagedUser as) throws Exception {
    return perform(path, as).andReturn().getResponse().getStatus();
  }

  private ResultActions perform(String path, ManagedUser as) throws Exception {
    return mockMvc.perform(get(path).with(authentication(auth(as))));
  }

  private static String roleOf(ManagedUser user) {
    return user.getRoles().toString();
  }

  private String[] all() {
    return java.util.stream.Stream
        .of(INFRA_ROUTES, ASSET_ROUTES, TENANT_ADMIN_ROUTES, RESOURCE_ADMIN_ROUTES,
            ANY_MANAGER_ROUTES)
        .flatMap(java.util.Arrays::stream)
        .toArray(String[]::new);
  }

  private ManagedUser user() {
    String username = "sec" + System.nanoTime();
    return userService.create(new UserForm(
        username, "일반 사용자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private ManagedUser withRole(UserRole role) {
    ManagedUser user = user();
    user.changeRole(role, OffsetDateTime.now());
    return user;
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }
}
