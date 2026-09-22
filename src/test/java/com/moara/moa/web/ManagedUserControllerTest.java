package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagedUserControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;

  @Test
  void listShowsTenantUsers() throws Exception {
    ManagedUser admin = createMoaUser();
    ManagedUser listed = createMoaUser();

    mockMvc.perform(get("/users").with(authentication(auth(admin))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(listed.getUsername())));
  }

  @Test
  void createsUserViaPostThenShowsInList() throws Exception {
    ManagedUser admin = createMoaUser();
    String username = "newuser" + System.nanoTime();

    mockMvc.perform(post("/users").with(authentication(auth(admin))).with(csrf())
            .param("username", username)
            .param("name", "신규사용자")
            .param("email", username + "@example.com")
            .param("phone", "010-1234-5678")
            .param("password", "safe-password-123")
            .param("passwordConfirm", "safe-password-123")
            .param("status", "ACTIVE"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/users"));

    mockMvc.perform(get("/users").with(authentication(auth(admin))))
        .andExpect(content().string(containsString(username)));
  }

  @Test
  void rejectsMismatchedPasswordConfirm() throws Exception {
    ManagedUser admin = createMoaUser();
    String username = "mm" + System.nanoTime();
    // 비밀번호와 확인값 불일치 → 폼 재표시(리다이렉트 아님).
    mockMvc.perform(post("/users").with(authentication(auth(admin))).with(csrf())
            .param("username", username).param("name", "불일치")
            .param("email", username + "@example.com").param("phone", "010-1234-5678")
            .param("password", "safe-password-123").param("passwordConfirm", "different-pass-999")
            .param("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("일치")));
  }

  @Test
  void rejectsMissingPhone() throws Exception {
    ManagedUser admin = createMoaUser();
    String username = "np" + System.nanoTime();
    // 전화번호 누락 → 검증 실패로 폼 재표시(리다이렉트 아님).
    mockMvc.perform(post("/users").with(authentication(auth(admin))).with(csrf())
            .param("username", username).param("name", "무전화")
            .param("email", username + "@example.com")
            .param("password", "safe-password-123").param("passwordConfirm", "safe-password-123")
            .param("status", "ACTIVE"))
        .andExpect(status().isOk());
  }

  @Test
  void createsAssetManagerWhenRoleSelected() throws Exception {
    ManagedUser admin = createMoaUser();
    String username = "am" + System.nanoTime();
    mockMvc.perform(post("/users").with(authentication(auth(admin))).with(csrf())
            .param("username", username).param("name", "자원관리자")
            .param("email", username + "@example.com").param("phone", "010-1234-5678")
            .param("password", "safe-password-123").param("passwordConfirm", "safe-password-123")
            .param("status", "ACTIVE").param("roles", "ASSET_MANAGER"))
        .andExpect(status().is3xxRedirection());
    assertEquals(java.util.Set.of(UserRole.ASSET_MANAGER), findByUsername(username).getRoles());
  }

  @Test
  void usernameIsDisplayNameKoreanOkEmailIsNot() throws Exception {
    // 로그인 키는 이메일. username은 표시 이름(핸들)이라 한글 허용·@ 불가.
    ManagedUser admin = createMoaUser();
    String email = "raytail" + System.nanoTime() + "@gmail.com";
    // 한글 표시 이름 + 이메일(로그인 ID) → 통과.
    mockMvc.perform(post("/users").with(authentication(auth(admin))).with(csrf())
            .param("username", "한지훈").param("name", "한지훈")
            .param("email", email).param("phone", "010-3094-7612")
            .param("password", "safe-password-123").param("passwordConfirm", "safe-password-123")
            .param("status", "ACTIVE"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/users"));

    // username에 @가 들어가면 검증 실패로 폼 재표시(리다이렉트 아님).
    mockMvc.perform(post("/users").with(authentication(auth(admin))).with(csrf())
            .param("username", "raytail@gmail.com").param("name", "한지훈")
            .param("email", "other" + System.nanoTime() + "@example.com").param("phone", "010-3094-7612")
            .param("password", "safe-password-123").param("passwordConfirm", "safe-password-123")
            .param("status", "ACTIVE"))
        .andExpect(status().isOk());
  }

  @Test
  void platformRoleEscalationIsStrippedToUser() throws Exception {
    ManagedUser admin = createMoaUser();
    String username = "esc" + System.nanoTime();
    // 기관 관리자는 기관 스코프 역할만 부여 가능 — SYSTEM_ADMIN(플랫폼) 부여 시도는 걸러져 일반 사용자.
    mockMvc.perform(post("/users").with(authentication(auth(admin))).with(csrf())
            .param("username", username).param("name", "탈취시도")
            .param("email", username + "@example.com").param("phone", "010-1234-5678")
            .param("password", "safe-password-123").param("passwordConfirm", "safe-password-123")
            .param("status", "ACTIVE").param("roles", "SYSTEM_ADMIN"))
        .andExpect(status().is3xxRedirection());
    assertEquals(java.util.Set.of(UserRole.USER), findByUsername(username).getRoles());
  }

  @Test
  void offboardTransitionsUserToOffboarded() throws Exception {
    ManagedUser admin = createMoaUser();
    ManagedUser leaver = createMoaUser();

    mockMvc.perform(post("/users/" + leaver.getId() + "/offboard")
            .with(authentication(auth(admin))).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/users"));

    assertEquals(UserStatus.OFFBOARDED, userService.findById(leaver.getId()).getStatus());
  }

  @Test
  void cannotOffboardSelf() throws Exception {
    ManagedUser admin = createMoaUser();

    mockMvc.perform(post("/users/" + admin.getId() + "/offboard")
            .with(authentication(auth(admin))).with(csrf()))
        .andExpect(status().is3xxRedirection());

    // 본인 계정은 그대로 활성 유지.
    assertEquals(UserStatus.ACTIVE, userService.findById(admin.getId()).getStatus());
  }

  private ManagedUser findByUsername(String username) {
    return userService.findByTenant(Tenant.DEFAULT_TENANT_ID).stream()
        .filter(u -> u.getUsername().equals(username))
        .findFirst().orElseThrow();
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "",
        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
  }

  private ManagedUser createMoaUser() {
    String username = "user" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
