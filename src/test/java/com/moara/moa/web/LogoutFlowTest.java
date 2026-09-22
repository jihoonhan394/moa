package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 로그아웃 점검: 사이드바 폼에 CSRF 토큰이 렌더되고, POST /logout이 세션 무효화 후 /enter?logout으로 착지. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  private ManagedUser admin() {
    long n = System.nanoTime();
    Tenant t = tenantService.createTenant(new CreateTenantCommand("로그아웃사", "LO" + n));
    return userService.create(t.getId(),
        new UserForm("관리자" + n, "관리자", "lo" + n + "@test.com", "010-1234-5678", "safe-password-123", UserStatus.ACTIVE),
        Set.of(UserRole.TENANT_ADMIN));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser u) {
    return new UsernamePasswordAuthenticationToken(
        new MoaUserDetails(u), "", List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN")));
  }

  @Test
  void dashboardRendersLogoutFormWithCsrfToken() throws Exception {
    mockMvc.perform(get("/dashboard").with(authentication(auth(admin()))))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("action=\"/logout\"")))
        .andExpect(content().string(Matchers.containsString("name=\"_csrf\"")));
  }

  @Test
  void postLogoutInvalidatesSessionAndRedirects() throws Exception {
    mockMvc.perform(post("/logout").with(authentication(auth(admin()))).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/enter?logout"))
        .andExpect(unauthenticated());
  }

  @Test
  void postLogoutWithoutCsrfIsForbidden() throws Exception {
    // CSRF 토큰 없이 로그아웃 시도는 403(폼이 토큰을 렌더해야 실제 로그아웃이 동작함을 방증).
    mockMvc.perform(post("/logout").with(authentication(auth(admin()))))
        .andExpect(status().isForbidden());
  }
}
