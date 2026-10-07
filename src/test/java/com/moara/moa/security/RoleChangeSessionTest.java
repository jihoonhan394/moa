package com.moara.moa.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.context.ActiveProfiles;

/**
 * 역할이 바뀌면 그 사람의 세션이 만료된다.
 *
 * <p>권한은 로그인 시점에 {@link MoaUserDetails} 로 <b>복사</b>되어 세션에 담긴다. 세션을
 * 끊지 않으면 <b>준 권한은 안 먹고, 뺀 권한은 계속 먹는다.</b> 뒤쪽이 더 위험하다 —
 * 자격증명 열람·솔루션 제어를 회수했는데 그 사람이 로그아웃할 때까지 계속 쓸 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoleChangeSessionTest {

  @Autowired private ManagedUserService userService;
  @Autowired private TenantService tenantService;
  @Autowired private SessionRegistry sessionRegistry;
  @Autowired private SessionTerminator sessionTerminator;
  @Autowired private com.moara.moa.user.UserLifecycleService lifecycleService;

  @Test
  @DisplayName("⚠️ 역할을 바꾸면 그 사람의 세션이 만료된다")
  void 역할_변경이_세션을_끊는다() {
    UUID tenant = newTenant();
    ManagedUser user = newUser(tenant, Set.of(UserRole.USER));
    String sessionId = openSession(user);

    userService.assignRoles(tenant, user.getId(), Set.of(UserRole.ASSET_MANAGER));

    assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired())
        .as("역할이 바뀌었는데 세션이 살아 있으면 예전 권한이 그대로 쓰인다")
        .isTrue();
  }

  @Test
  @DisplayName("⚠️ 권한을 회수하면 즉시 끊긴다 — 이쪽이 더 위험한 방향이다")
  void 권한_회수가_즉시_반영된다() {
    UUID tenant = newTenant();
    ManagedUser user = newUser(tenant, Set.of(UserRole.ASSET_MANAGER));
    String sessionId = openSession(user);

    // 자산 관리 권한을 뺀다 → 그 사람은 로그아웃 전까지 계속 쓸 수 있어선 안 된다.
    userService.assignRoles(tenant, user.getId(), Set.of(UserRole.USER));

    assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired()).isTrue();
  }

  @Test
  @DisplayName("역할이 그대로면 세션을 끊지 않는다 — 괜히 로그아웃시키지 않는다")
  void 같은_역할로_저장하면_끊지_않는다() {
    UUID tenant = newTenant();
    ManagedUser user = newUser(tenant, Set.of(UserRole.ASSET_MANAGER));
    String sessionId = openSession(user);

    // 다른 항목만 고치고 저장하는 경우가 잦다. 그때마다 로그아웃되면 쓸 수 없다.
    userService.assignRoles(tenant, user.getId(), Set.of(UserRole.ASSET_MANAGER));

    assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired()).isFalse();
  }

  @Test
  @DisplayName("⚠️ 남의 세션은 건드리지 않는다")
  void 다른_사람의_세션은_그대로다() {
    UUID tenant = newTenant();
    ManagedUser changed = newUser(tenant, Set.of(UserRole.USER));
    ManagedUser other = newUser(tenant, Set.of(UserRole.USER));
    String changedSession = openSession(changed);
    String otherSession = openSession(other);

    userService.assignRoles(tenant, changed.getId(), Set.of(UserRole.ASSET_MANAGER));

    assertThat(sessionRegistry.getSessionInformation(changedSession).isExpired()).isTrue();
    assertThat(sessionRegistry.getSessionInformation(otherSession).isExpired()).isFalse();
  }

  @Test
  @DisplayName("한 사람이 여러 기기로 들어와 있으면 전부 끊는다")
  void 여러_기기를_모두_끊는다() {
    UUID tenant = newTenant();
    ManagedUser user = newUser(tenant, Set.of(UserRole.USER));
    String pc = openSession(user);
    String phone = openSession(user);

    userService.assignRoles(tenant, user.getId(), Set.of(UserRole.ASSET_MANAGER));

    assertThat(sessionRegistry.getSessionInformation(pc).isExpired()).isTrue();
    assertThat(sessionRegistry.getSessionInformation(phone).isExpired()).isTrue();
  }

  @Test
  @DisplayName("세션이 없어도 터지지 않는다 — 초기 데이터 시드가 그 경로다")
  void 세션이_없어도_된다() {
    assertThat(sessionTerminator.expireSessionsOf(UUID.randomUUID())).isZero();
    assertThat(sessionTerminator.expireSessionsOf(null)).isZero();
  }

  @Test
  @DisplayName("⚠️ 퇴사 처리하면 열린 세션이 끊긴다 — 상태만 바꾸면 새 로그인만 막힌다")
  void 퇴사가_세션을_끊는다() {
    UUID tenant = newTenant();
    ManagedUser user = newUser(tenant, Set.of(UserRole.USER));
    String sessionId = openSession(user);

    lifecycleService.offboard(tenant, user.getId());

    assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired())
        .as("퇴사자가 로그아웃할 때까지 계속 쓸 수 있으면 안 된다")
        .isTrue();
  }

  @Test
  @DisplayName("⚠️ 계정을 정지하면 열린 세션이 끊긴다")
  void 정지가_세션을_끊는다() {
    UUID tenant = newTenant();
    ManagedUser user = newUser(tenant, Set.of(UserRole.USER));
    String sessionId = openSession(user);

    userService.deactivate(tenant, user.getId());

    assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired()).isTrue();
  }

  @Test
  @DisplayName("⚠️ 계정을 지우면 열린 세션이 끊긴다 — 없는 사용자로 돌아다니면 안 된다")
  void 삭제가_세션을_끊는다() {
    UUID tenant = newTenant();
    ManagedUser user = newUser(tenant, Set.of(UserRole.USER));
    String sessionId = openSession(user);

    userService.delete(user.getId());

    assertThat(sessionRegistry.getSessionInformation(sessionId).isExpired()).isTrue();
  }

  /**
   * 그 사람이 로그인한 상태를 만든다.
   *
   * <p>⚠️ 레지스트리는 principal <b>객체</b>로 묶으므로, 세션마다 새 {@link MoaUserDetails}
   * 를 만든다 — 실제 로그인도 매번 새로 만든다. {@code SessionTerminator} 가 객체 동일성이
   * 아니라 사용자 식별자로 찾는다는 것을 이 테스트가 함께 확인한다.
   */
  private String openSession(ManagedUser user) {
    String sessionId = UUID.randomUUID().toString();
    sessionRegistry.registerNewSession(sessionId, new MoaUserDetails(user));
    return sessionId;
  }

  private UUID newTenant() {
    return tenantService.createTenant(
        new CreateTenantCommand("세션사", "SS" + System.nanoTime())).getId();
  }

  private ManagedUser newUser(UUID tenantId, Set<UserRole> roles) {
    String name = "sess" + System.nanoTime();
    return userService.create(tenantId, new UserForm(
        name, "세션시험", name + "@example.com", "safe-password-123", UserStatus.ACTIVE), roles);
  }
}
