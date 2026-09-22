package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.FeatureModule;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 기능 엔타이틀먼트(라우트 차단)와 구독 만기 로그인 차단을 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantEntitlementTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void featureGatingBlocksModulesTenantDoesNotHave() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("카카오", "KAKAO" + System.nanoTime()));
    tenantService.updateDetail(tenant.getId(), null, null, Set.of(FeatureModule.ASSETS));
    ManagedUser manager = userService.createTenantAdmin(
        tenant.getId(), "am" + System.nanoTime(), "자원관리자",
        "am" + System.nanoTime() + "@example.com", "010-0000-0000", "safe-password-123");
    // 자원 화면은 인프라 관리자(INFRA_MANAGER) 역할이라야 접근 — 이 테스트는 '기능' 게이팅을 검증한다.
    manager.changeRole(UserRole.INFRA_MANAGER, java.time.OffsetDateTime.now());
    var auth = auth(manager);

    // ASSETS는 보유 → 접근 가능
    mockMvc.perform(get("/assets").with(authentication(auth))).andExpect(status().isOk());
    // SOLUTIONS/CREDENTIALS 미보유 → 라우트 차단(403)
    mockMvc.perform(get("/solutions").with(authentication(auth))).andExpect(status().isForbidden());
    mockMvc.perform(get("/credentials").with(authentication(auth))).andExpect(status().isForbidden());
  }

  @Test
  void expiredSubscriptionRejectedAtEntry() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("만료사", "EXPIRED" + System.nanoTime()));
    tenantService.updateDetail(tenant.getId(), null, LocalDate.now().minusDays(1), Set.of(FeatureModule.ASSETS));

    // 만기 기관은 진입(/enter) 단계에서 막혀 로그인창(/login)으로 넘어가지 않는다.
    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/enter").param("code", tenant.getCode()).session(session).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("enter"));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }
}
