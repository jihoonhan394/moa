package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

/**
 * 역할별 라우트 접근 제어. 직무 분리:
 *   기관 관리자(TENANT_ADMIN) = 사용자·조직·감사, 인프라 관리자(INFRA_MANAGER) = 자산·서버·솔루션·자격증명·접근권한.
 *   서로의 화면에는 들어갈 수 없고, 일반 사용자는 둘 다 차단된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoleBasedAccessControlTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;

  @Test
  void userIsForbiddenFromAllManagementRoutes() throws Exception {
    ManagedUser user = createUser();
    for (String path : new String[] {"/users", "/groups", "/audit", "/assets", "/servers", "/permissions"}) {
      mockMvc.perform(get(path).with(authentication(auth(user))))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void tenantAdminAccessesGovernanceButNotResources() throws Exception {
    ManagedUser admin = tenantAdmin();
    // 거버넌스(사람·조직·감사)는 허용.
    for (String path : new String[] {"/users", "/groups", "/audit"}) {
      mockMvc.perform(get(path).with(authentication(auth(admin)))).andExpect(status().isOk());
    }
    // 자원 화면·접근권한은 자원 관리자 몫 → 기관 관리자 차단.
    for (String path : new String[] {"/assets", "/servers", "/solutions", "/credentials", "/permissions"}) {
      mockMvc.perform(get(path).with(authentication(auth(admin)))).andExpect(status().isForbidden());
    }
  }

  @Test
  void assetManagerAccessesResourcesButNotGovernance() throws Exception {
    ManagedUser manager = infraManager();
    // 기본 테넌트(MOA)는 전 기능 보유 → 자원 화면·접근권한 허용.
    for (String path : new String[] {"/assets", "/servers", "/solutions", "/credentials", "/permissions"}) {
      mockMvc.perform(get(path).with(authentication(auth(manager)))).andExpect(status().isOk());
    }
    // 사람·조직·감사(거버넌스)와 플랫폼 콘솔은 차단.
    for (String path : new String[] {"/users", "/groups", "/audit", "/admin"}) {
      mockMvc.perform(get(path).with(authentication(auth(manager)))).andExpect(status().isForbidden());
    }
  }

  @Test
  void userCanAccessPortal() throws Exception {
    ManagedUser user = createUser();
    mockMvc.perform(get("/portal").with(authentication(auth(user))))
        .andExpect(status().isOk());
  }

  @Test
  void assetManagerSeesAccessPermissionMenuUserDoesNot() throws Exception {
    mockMvc.perform(get("/portal").with(authentication(auth(createUser()))))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("/permissions"))));
    mockMvc.perform(get("/assets").with(authentication(auth(infraManager()))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("/permissions")));
  }

  private ManagedUser createUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private ManagedUser tenantAdmin() {
    return withRole(UserRole.TENANT_ADMIN);
  }

  private ManagedUser infraManager() {
    return withRole(UserRole.INFRA_MANAGER);
  }

  private ManagedUser withRole(UserRole role) {
    ManagedUser user = createUser();
    user.changeRole(role, OffsetDateTime.now());
    return user;
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }
}
