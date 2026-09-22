package com.moara.moa.web;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 접근 현황: 기관 관리자만 열람(자원 관리자·일반 사용자 403), 자원 관리자 명단이 노출되는지 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessReviewTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void tenantAdminSeesReviewOthersForbidden() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("검토사", "REV" + System.nanoTime()));
    ManagedUser admin = userService.createTenantAdmin(
        tenant.getId(), "ta" + System.nanoTime(), "관리자",
        "adm" + System.nanoTime() + "@example.com", "010-0000-0000", "safe-password-123");
    String mgrName = "자원관리자" + System.nanoTime();
    String mgrUser = "am" + System.nanoTime();
    ManagedUser manager = userService.create(tenant.getId(),
        new UserForm(mgrUser, mgrName, mgrUser + "@example.com", "010-1111-2222", "safe-password-123", UserStatus.ACTIVE),
        UserRole.INFRA_MANAGER);

    // 기관 관리자: 200 + 인프라 관리자 명단에 노출.
    mockMvc.perform(get("/access-review").with(authentication(auth(admin))))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString(mgrName)));

    // 자원 관리자·일반 사용자: 차단(403).
    mockMvc.perform(get("/access-review").with(authentication(auth(manager))))
        .andExpect(status().isForbidden());
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }
}
