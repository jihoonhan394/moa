package com.moara.moa.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 두레이식 2단계 진입(기관 선택 → 로그인)과 기관 스코프 인증을 필터체인 전체로 검증한다.
 * 특히 아이디가 다른 기관에 속하면 인증되지 않음을 확인한다(교차테넌트 방지).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntryLoginFlowTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private com.moara.moa.group.AccessGroupService groupService;
  @Autowired private com.moara.moa.audit.AuditLogService auditLogService;

  @Test
  void selectsTenantThenLogsInScopedUser() throws Exception {
    String code = "MTCM" + System.nanoTime();
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("엠티씨엠", code));
    String username = "kim" + System.nanoTime();
    userService.create(
        tenant.getId(),
        new UserForm(username, "김엠", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/enter").param("code", code).session(session).with(csrf()))
        .andExpect(redirectedUrl("/login"));
    mockMvc.perform(post("/login")
            .param("username", username + "@example.com")
            .param("password", "safe-password-123")
            .session(session)
            .with(csrf()))
        .andExpect(redirectedUrl("/my/workspace")) // 일반 기관 사용자(USER) 착지 = 개인 허브(기능 무관, 항상 접근 가능)
        .andExpect(authenticated().withUsername(username));
  }

  /**
   * 회귀: 기능(ASSETS) 미보유 기관의 인프라·자산 관리자가 로그인 시 기능 게이트된 /assets로 착지해
   * 403이 나던 문제. 관리 역할은 기능 게이트 없는 /dashboard로 착지하고 그 페이지가 실제로 열려야 한다.
   */
  @Test
  void managerWithoutFeaturesLandsOnDashboardNotForbidden() throws Exception {
    String code = "NOFEAT" + System.nanoTime();
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("무기능사", code)); // 기능 없음(빈 집합)
    String username = "infra" + System.nanoTime();
    userService.create(
        tenant.getId(),
        new UserForm(username, "인프라", username + "@example.com", "safe-password-123", UserStatus.ACTIVE),
        Set.of(UserRole.INFRA_MANAGER, UserRole.ASSET_MANAGER));

    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/enter").param("code", code).session(session).with(csrf()))
        .andExpect(redirectedUrl("/login"));
    mockMvc.perform(post("/login")
            .param("username", username + "@example.com").param("password", "safe-password-123")
            .session(session).with(csrf()))
        .andExpect(redirectedUrl("/dashboard"))
        .andExpect(authenticated().withUsername(username));
    // 착지 페이지가 기능 인터셉터에 막히지 않고 실제로 열린다(과거엔 /assets → 403).
    mockMvc.perform(get("/dashboard").session(session))
        .andExpect(status().isOk());
  }

  /** 관리 역할 없는 부서장(그룹 leader)은 팀 콘솔(/team)로 착지한다. */
  @Test
  void departmentLeaderWithoutRoleLandsOnTeam() throws Exception {
    String code = "LEAD" + System.nanoTime();
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("부서장사", code));
    String username = "lead" + System.nanoTime();
    var user = userService.create(tenant.getId(),
        new UserForm(username, "부서장", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
    var group = groupService.create(tenant.getId(),
        new com.moara.moa.group.AccessGroupForm("팀-" + System.nanoTime(), null,
            com.moara.moa.group.AccessGroupStatus.ACTIVE, null));
    groupService.addMember(tenant.getId(), group.getId(), user.getId());
    groupService.setLeader(tenant.getId(), group.getId(), user.getId(), true);

    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/enter").param("code", code).session(session).with(csrf()))
        .andExpect(redirectedUrl("/login"));
    mockMvc.perform(post("/login")
            .param("username", username + "@example.com").param("password", "safe-password-123")
            .session(session).with(csrf()))
        .andExpect(redirectedUrl("/team"))
        .andExpect(authenticated().withUsername(username));
  }

  /** 성공 로그인은 USER_LOGIN 감사로 남아 접속 이력(로그인 이력) 화면의 소스가 된다. */
  @Test
  void successfulLoginIsAuditedForAccessHistory() throws Exception {
    String code = "LOGH" + System.nanoTime();
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("로그사", code));
    String username = "kim" + System.nanoTime();
    var user = userService.create(tenant.getId(),
        new UserForm(username, "김엠", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/enter").param("code", code).session(session).with(csrf()));
    mockMvc.perform(post("/login").param("username", username + "@example.com").param("password", "safe-password-123")
            .session(session).with(csrf()))
        .andExpect(authenticated().withUsername(username));

    org.assertj.core.api.Assertions.assertThat(
            auditLogService.findRecentByAction(tenant.getId(), "USER_LOGIN"))
        .anyMatch(a -> a.getActorUserId().equals(user.getId()));
  }

  @Test
  void rejectsLoginWhenUsernameBelongsToAnotherTenant() throws Exception {
    // 기본 테넌트(MOA)에만 존재하는 아이디.
    String username = "solo" + System.nanoTime();
    userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));

    String otherCode = "OTHER" + System.nanoTime();
    tenantService.createTenant(new CreateTenantCommand("타사", otherCode));

    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/enter").param("code", otherCode).session(session).with(csrf()))
        .andExpect(redirectedUrl("/login"));
    // 다른 기관 컨텍스트에서는 이 아이디로 로그인할 수 없다.
    mockMvc.perform(post("/login")
            .param("username", username + "@example.com")
            .param("password", "safe-password-123")
            .session(session)
            .with(csrf()))
        .andExpect(redirectedUrl("/login?error"))
        .andExpect(unauthenticated());
  }

  @Test
  void unknownTenantCodeStaysOnEnter() throws Exception {
    mockMvc.perform(post("/enter").param("code", "NOSUCH" + System.nanoTime()).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("enter"));
  }

  @Test
  void loginWithoutTenantSelectionRedirectsToEnter() throws Exception {
    mockMvc.perform(get("/login")).andExpect(redirectedUrl("/enter"));
  }

  @Test
  void unauthenticatedProtectedPageRedirectsToEnter() throws Exception {
    mockMvc.perform(get("/dashboard")).andExpect(redirectedUrl("/enter"));
  }
}
