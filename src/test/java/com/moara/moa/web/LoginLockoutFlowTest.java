package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.moara.moa.audit.AuditLog;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.LoginThrottle;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 실패가 실제 요청 경로에서 세지고 기록되는지.
 *
 * <p>{@code LoginThrottleTest}가 판단을 보고, 여기서는 그 판단이 <b>폼 로그인에 실제로 꽂혀
 * 있는지</b>를 본다 — 정책을 만들어 놓고 연결하지 않는 것이 이런 기능의 흔한 실패 방식이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginLockoutFlowTest {
  private static final String PASSWORD = "safe-password-123";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;
  @Autowired private AuditLogService auditLogService;

  /**
   * 임계값을 넘기면 <b>맞는 비밀번호도</b> 막혀야 한다. 틀린 것만 막고 맞는 것을 통과시키면
   * 잠금이 아니라 그냥 지연이다.
   */
  @Test
  void correctPasswordIsRejectedOnceLocked() throws Exception {
    Fixture f = fixture();

    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT; i++) {
      attempt(f, f.loginId, "wrong-password-" + i).andExpect(redirectedUrl("/login?error"));
    }

    attempt(f, f.loginId, PASSWORD)
        .andExpect(redirectedUrl("/login?error"))
        .andExpect(unauthenticated());
  }

  /** 임계값 직전까지는 정상 로그인이 되어야 한다 — 오타 몇 번에 갇히면 제품을 못 쓴다. */
  @Test
  void rightPasswordStillWorksBeforeTheLimit() throws Exception {
    Fixture f = fixture();

    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT - 1; i++) {
      attempt(f, f.loginId, "wrong-password-" + i).andExpect(redirectedUrl("/login?error"));
    }

    attempt(f, f.loginId, PASSWORD).andExpect(authenticated());
  }

  /** 성공하면 카운터가 지워져, 다음에 또 실수할 여지가 원래대로 돌아온다. */
  @Test
  void successResetsTheCounter() throws Exception {
    Fixture f = fixture();

    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT - 1; i++) {
      attempt(f, f.loginId, "wrong-" + i);
    }
    attempt(f, f.loginId, PASSWORD).andExpect(authenticated());

    // 초기화되지 않았다면 여기서 곧바로 잠긴다.
    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT - 1; i++) {
      attempt(f, f.loginId, "wrong-again-" + i);
    }
    attempt(f, f.loginId, PASSWORD).andExpect(authenticated());
  }

  /** 실패는 감사에 남는다. 실재하는 계정이면 그 사람이 행위자다. */
  @Test
  void failureOfKnownAccountIsAudited() throws Exception {
    Fixture f = fixture();

    attempt(f, f.loginId, "definitely-wrong");

    AuditLog log = lastLoginFailure(f.tenant);
    assertEquals(AuditResult.FAILURE, log.getResult());
    assertEquals(f.user.getId(), log.getActorUserId());
    assertTrue(log.getMessage().contains("ip="), log.getMessage());
  }

  /**
   * <b>없는 계정으로의 시도도 남아야 한다.</b> 사용자명을 훑는 공격이 정확히 그 형태라,
   * 이것을 못 적으면 가장 보고 싶은 공격이 감사에서 통째로 빠진다. V72에서 행위자를 선택
   * 항목으로 바꾼 이유가 이 한 줄이다.
   */
  @Test
  void failureOfUnknownAccountIsAuditedWithoutActor() throws Exception {
    Fixture f = fixture();

    attempt(f, "no-such-user-" + System.nanoTime() + "@example.com", "whatever");

    AuditLog log = lastLoginFailure(f.tenant);
    assertNull(log.getActorUserId(), "없는 계정의 시도에 행위자가 붙었다");
    assertEquals(AuditResult.FAILURE, log.getResult());
  }

  /** 기록된 아이디는 마스킹된다 — 감사 화면과 로그에 그대로 노출할 이유가 없다. */
  @Test
  void auditedIdentifierIsMasked() throws Exception {
    Fixture f = fixture();
    String local = "verysecretlogin" + System.nanoTime();

    attempt(f, local + "@example.com", "whatever");

    AuditLog log = lastLoginFailure(f.tenant);
    assertTrue(!log.getMessage().contains(local), "아이디가 그대로 남았다: " + log.getMessage());
    assertTrue(log.getMessage().contains("***"), log.getMessage());
  }

  // ── 도우미 ────────────────────────────────────────────────────────────────

  /** loginId는 이메일이다 — 기관 사용자의 로그인 키(username은 표시용 이름). */
  private record Fixture(Tenant tenant, String code, ManagedUser user, String loginId) {}

  private Fixture fixture() {
    String code = "LOCK" + System.nanoTime();
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("잠금테스트", code));
    String username = "user" + System.nanoTime();
    ManagedUser user = userService.create(tenant.getId(), new UserForm(
        username, "홍길동", username + "@example.com", PASSWORD, UserStatus.ACTIVE));
    return new Fixture(tenant, code, user, username + "@example.com");
  }

  /** 진입 1단계(기관 선택) → 2단계(로그인). 매번 새 세션으로 한 번의 시도를 만든다. */
  private org.springframework.test.web.servlet.ResultActions attempt(
      Fixture f, String loginId, String password) throws Exception {
    MockHttpSession session = new MockHttpSession();
    mockMvc.perform(post("/enter").param("code", f.code).session(session).with(csrf()));
    return mockMvc.perform(post("/login")
        .param("username", loginId)
        .param("password", password)
        .session(session)
        .with(csrf()));
  }

  private AuditLog lastLoginFailure(Tenant tenant) {
    List<AuditLog> logs = auditLogService.findRecentByAction(tenant.getId(), "USER_LOGIN_FAIL");
    assertTrue(!logs.isEmpty(), "로그인 실패가 감사에 남지 않았다");
    return logs.get(0);
  }
}
