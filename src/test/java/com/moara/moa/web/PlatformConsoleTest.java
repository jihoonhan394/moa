package com.moara.moa.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 플랫폼 콘솔: 동일 UI 뼈대에서 SYSTEM_ADMIN에게 플랫폼 메뉴만 노출되고 기관 업무 메뉴는 숨는지,
 * 운영자 계정 생성/접근 제어가 동작하는지 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformConsoleTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;
  @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

  @Test
  void platformConsoleShowsPlatformMenusNotTenantMenus() throws Exception {
    var auth = auth(systemAdmin());
    mockMvc.perform(get("/admin").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/overview"))
        .andExpect(content().string(containsString("/admin/tenants")))
        .andExpect(content().string(containsString("/admin/operators")))
        // 기관 업무 메뉴/포털은 플랫폼 콘솔에 노출되지 않는다.
        .andExpect(content().string(not(containsString("/solutions"))))
        .andExpect(content().string(not(containsString("/credentials"))))
        .andExpect(content().string(not(containsString("내 자산 접속"))));
  }

  @Test
  void createsOperatorAccount() throws Exception {
    var auth = auth(systemAdmin());
    String opName = "op" + System.nanoTime();
    mockMvc.perform(post("/admin/operators").with(authentication(auth)).with(csrf())
            .param("username", opName).param("name", "새 운영자")
            .param("email", opName + "@example.com").param("phone", "010-1234-5678")
            .param("password", "safe-password-123").param("passwordConfirm", "safe-password-123"))
        .andExpect(redirectedUrl("/admin/operators"));
    mockMvc.perform(get("/admin/operators").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(opName)));
  }

  @Test
  void tenantAdminCannotAccessPlatformConsole() throws Exception {
    var auth = auth(tenantAdmin());
    mockMvc.perform(get("/admin").with(authentication(auth))).andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/operators").with(authentication(auth))).andExpect(status().isForbidden());
  }

  /** 플랫폼(테넌시) 관리자는 소속 기관이 없으므로 기관 관리자 화면(자산/사용자/권한 등)에 URL로도 들어갈 수 없다. */
  @Test
  void platformAdminCannotAccessTenantWorkspaceRoutes() throws Exception {
    var auth = auth(systemAdmin());
    for (String path : new String[] {
        "/users", "/assets", "/servers", "/groups", "/permissions", "/audit",
        "/solutions", "/credentials", "/access-history", "/mail"}) {
      mockMvc.perform(get(path).with(authentication(auth)))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void unlimitedSubscriptionClearsBothDates() throws Exception {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("무제한사", "UNL" + System.nanoTime()));
    tenantService.updateDetail(tenant.getId(),
        LocalDate.now(), LocalDate.now().plusDays(30), Set.of(FeatureModule.ASSETS));

    // 무제한 체크 시 제출된 날짜를 무시하고 시작일·만기일 모두 null 저장
    mockMvc.perform(post("/admin/tenants/" + tenant.getId()).with(authentication(auth(systemAdmin()))).with(csrf())
            .param("unlimited", "on")
            .param("subscriptionStart", "2026-01-01")
            .param("subscriptionEnd", "2026-12-31")
            .param("features", "ASSETS"))
        .andExpect(status().is3xxRedirection());

    Tenant after = tenantService.getById(tenant.getId());
    assertNull(after.getSubscriptionStart());
    assertNull(after.getSubscriptionEnd());
  }

  @Test
  void managesTenantAdminLifecycle() throws Exception {
    var auth = auth(systemAdmin());
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("운영사", "OPS" + System.nanoTime()));
    ManagedUser admin = userService.createTenantAdmin(
        tenant.getId(), "ta" + System.nanoTime(), "옛이름",
        "adm" + System.nanoTime() + "@example.com", "010-0000-0000", "safe-password-123");
    String base = "/admin/tenants/" + tenant.getId() + "/admins/" + admin.getId();

    // 수정: 이름·연락처 변경(+비밀번호 재설정, 확인 일치) → 반영.
    mockMvc.perform(post(base + "/update").with(authentication(auth)).with(csrf())
            .param("name", "새이름")
            .param("email", "changed" + System.nanoTime() + "@example.com").param("phone", "010-9999-8888")
            .param("password", "new-password-456").param("passwordConfirm", "new-password-456"))
        .andExpect(redirectedUrl("/admin/tenants/" + tenant.getId()));
    assertEquals("새이름", userService.findById(admin.getId()).getName());

    // 비활성 → DISABLED.
    mockMvc.perform(post(base + "/disable").with(authentication(auth)).with(csrf()))
        .andExpect(redirectedUrl("/admin/tenants/" + tenant.getId()));
    assertEquals(UserStatus.DISABLED, userService.findById(admin.getId()).getStatus());

    // 활성 → ACTIVE.
    mockMvc.perform(post(base + "/enable").with(authentication(auth)).with(csrf()))
        .andExpect(redirectedUrl("/admin/tenants/" + tenant.getId()));
    assertEquals(UserStatus.ACTIVE, userService.findById(admin.getId()).getStatus());

    // 삭제(활동 이력 없음) → 목록에서 사라짐.
    mockMvc.perform(post(base + "/delete").with(authentication(auth)).with(csrf()))
        .andExpect(redirectedUrl("/admin/tenants/" + tenant.getId()));
    assertTrue(userService.findTenantAdmins(tenant.getId()).isEmpty());
  }

  /**
   * 부트스트랩(ensureSystemAdmin)은 플랫폼 풀(tenant_id IS NULL)로만 조회하며 멱등이어야 한다.
   * 참고: 운영 버그(기관 스코프에 같은 'admin'이 생겨 전역 조회가 다건→NonUniqueResult로 기동 실패)의
   * 정확한 재현은 H2에서 불가하다 — V16의 전역 username UNIQUE 드롭이 H2에선 제약명 불일치로 no-op라
   * 테스트 스키마엔 전역 UNIQUE가 남아 동일 아이디 2행을 만들 수 없다(운영 PostgreSQL에선 드롭됨).
   * 그래서 여기서는 새 조회 경로가 정상·멱등 동작하는지를 검증한다.
   */
  @Test
  void ensureSystemAdminIsIdempotentOnPlatformPool() {
    String username = "sysadmin" + System.nanoTime();
    ManagedUser first = userService.ensureSystemAdmin(username, "safe-password-123");
    ManagedUser again = userService.ensureSystemAdmin(username, "safe-password-456");
    assertEquals(first.getId(), again.getId());
    assertTrue(again.hasRole(UserRole.SYSTEM_ADMIN));
    assertNull(again.getTenantId());
  }

  @Test
  void provisionAdminStoresExactPassword() throws Exception {
    var auth = auth(systemAdmin());
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("비번검증사", "PW" + System.nanoTime()));
    String uname = "ta" + System.nanoTime();
    String pw = "Str0ng-Pass!23";
    mockMvc.perform(post("/admin/tenants/" + tenant.getId() + "/admins")
            .with(authentication(auth)).with(csrf())
            .param("username", uname).param("name", "관리자")
            .param("email", uname + "@example.com").param("phone", "010-1234-5678")
            .param("password", pw).param("passwordConfirm", pw))
        .andExpect(status().is3xxRedirection());

    // 플랫폼 콘솔의 대표 관리자 생성이 입력한 비밀번호를 '그대로' 저장하는지 검증(해시 왕복).
    ManagedUser created = userService.findTenantAdmins(tenant.getId()).stream()
        .filter(u -> u.getUsername().equals(uname)).findFirst().orElseThrow();
    assertTrue(passwordEncoder.matches(pw, created.getPasswordHash()),
        "프로비저닝된 비밀번호가 입력값과 일치해야 한다");
  }

  private ManagedUser systemAdmin() {
    return userService.ensureSystemAdmin("sysadm" + System.nanoTime(), "safe-password-123");
  }

  private ManagedUser tenantAdmin() {
    String username = "ta" + System.nanoTime();
    ManagedUser user = userService.create(new UserForm(
        username, "기관운영자", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
    user.changeRole(UserRole.TENANT_ADMIN, OffsetDateTime.now());
    return user;
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
  }
}
