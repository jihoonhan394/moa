package com.moara.moa.solution;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.notification.Notification;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 헬스 체크를 <b>실제 소켓</b>에 대고 돌린다. 포트를 모킹하지 않는 이유는, 이 기능에서 틀릴
 * 만한 곳이 판정 분기보다 "진짜 닫힌 포트에 붙었을 때 무슨 예외가 오나"쪽이기 때문이다.
 *
 * <p>테스트가 직접 {@link ServerSocket} 을 열어 포트를 잡고, 닫으면 그 포트가 바로 거절
 * 상태가 된다 — 외부 호스트에 의존하지 않고 UP → DOWN → 복구를 한 테스트에서 재현할 수 있다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SolutionHealthServiceTest {

  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @Autowired private SolutionHealthService healthService;
  @Autowired private ManagedSolutionService solutionService;
  @Autowired private SolutionAccessService accessService;
  @Autowired private SolutionHealthRepository healthRepository;
  @Autowired private AssetService assetService;
  @Autowired private ManagedUserService userService;
  @Autowired private NotificationService notificationService;
  @Autowired private TenantService tenantService;

  @Test
  @DisplayName("열린 포트는 UP, 닫히면 연속 2회 뒤 DOWN, 다시 열면 UP")
  void 포트를_실제로_찔러_본다() throws IOException {
    ManagedUser owner = user();
    ManagedSolution solution;
    int port;

    try (ServerSocket listener = new ServerSocket(0)) {
      port = listener.getLocalPort();
      solution = tcpSolution(port);
      accessService.assignUser(TENANT, solution.getId(), owner.getId());

      healthService.checkTenant(TENANT);
      assertThat(state(solution).getState()).isEqualTo(HealthState.UP);
    }

    // 소켓을 닫았다 — 이제 그 포트는 접속을 거절한다.
    healthService.checkTenant(TENANT);
    assertThat(state(solution).getState())
        .as("1회 실패로는 장애가 아니다")
        .isEqualTo(HealthState.UP);

    healthService.checkTenant(TENANT);
    SolutionHealth down = state(solution);
    assertThat(down.getState()).isEqualTo(HealthState.DOWN);
    assertThat(down.getDetail()).contains(String.valueOf(port));

    // 장애 알림이 배정자에게 갔다.
    assertThat(titlesFor(owner)).anyMatch(title -> title.startsWith("[장애] " + solution.getName()));

    // 다시 열면 복구다.
    try (ServerSocket reopened = new ServerSocket(port)) {
      assertThat(reopened.getLocalPort()).isEqualTo(port);
      healthService.checkTenant(TENANT);
    }
    assertThat(state(solution).getState()).isEqualTo(HealthState.UP);
    assertThat(titlesFor(owner)).anyMatch(title -> title.startsWith("[복구] " + solution.getName()));
  }

  @Test
  @DisplayName("⚠️ DOWN 이 지속되는 동안 알림이 늘지 않는다")
  void 장애_지속_중_알림이_늘지_않는다() {
    ManagedUser owner = user();
    ManagedSolution solution = tcpSolution(closedPort());
    accessService.assignUser(TENANT, solution.getId(), owner.getId());

    healthService.checkTenant(TENANT);
    healthService.checkTenant(TENANT); // 여기서 DOWN + 알림
    long afterFirstAlert = countFor(owner);
    assertThat(afterFirstAlert).isPositive();

    for (int round = 0; round < 5; round++) {
      healthService.checkTenant(TENANT);
    }

    assertThat(countFor(owner))
        .as("상태가 그대로면 알림도 그대로여야 한다")
        .isEqualTo(afterFirstAlert);
  }

  @Test
  @DisplayName("⚠️ 자격증명이 없는 COMMAND 점검은 UNKNOWN 이고 알리지 않는다 — 설정 공백은 장애가 아니다")
  void 설정_공백은_장애로_올리지_않는다() {
    ManagedUser owner = user();
    Asset server = server();
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "cmd-" + System.nanoTime(), SolutionType.CUSTOM_COMMAND, "app",
        null, HealthCheckType.COMMAND, "systemctl is-active app", SolutionStatus.ACTIVE,
        null, null, null, RemoteProtocol.SSH, null));
    accessService.assignUser(TENANT, solution.getId(), owner.getId());

    healthService.checkTenant(TENANT);
    healthService.checkTenant(TENANT);

    SolutionHealth health = state(solution);
    assertThat(health.getState()).isEqualTo(HealthState.UNKNOWN);
    assertThat(health.getDetail()).contains("자격증명");
    assertThat(titlesFor(owner)).noneMatch(title -> title.contains(solution.getName()));
  }

  @Test
  @DisplayName("점검 방식이 NONE 이면 아예 점검하지 않는다")
  void NONE_은_점검하지_않는다() {
    Asset server = server();
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "none-" + System.nanoTime(), SolutionType.LINUX_DAEMON, "x.service",
        null, HealthCheckType.NONE, null, SolutionStatus.ACTIVE,
        null, null, null, RemoteProtocol.SSH, null));

    healthService.checkTenant(TENANT);

    assertThat(healthService.stateOf(TENANT, solution.getId()))
        .as("점검하지 않은 솔루션에는 상태 행을 만들지 않는다")
        .isNull();
  }

  @Test
  @DisplayName("꺼 둔 솔루션(DISABLED)은 점검하지 않는다 — 안 돌리기로 한 것이 안 도는 건 장애가 아니다")
  void 꺼_둔_솔루션은_건너뛴다() {
    Asset server = server();
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "off-" + System.nanoTime(), SolutionType.LINUX_DAEMON, "x.service",
        null, HealthCheckType.TCP_PORT, String.valueOf(closedPort()), SolutionStatus.DISABLED,
        null, null, null, RemoteProtocol.SSH, null));

    healthService.checkTenant(TENANT);
    healthService.checkTenant(TENANT);

    assertThat(healthService.stateOf(TENANT, solution.getId())).isNull();
  }

  @Test
  @DisplayName("점검 대상이 포트 번호가 아니면 UNKNOWN 이다(장애가 아니다)")
  void 포트가_아닌_대상은_UNKNOWN() {
    Asset server = server();
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "bad-" + System.nanoTime(), SolutionType.LINUX_DAEMON, "x.service",
        null, HealthCheckType.TCP_PORT, "여덟시팔십", SolutionStatus.ACTIVE,
        null, null, null, RemoteProtocol.SSH, null));

    healthService.checkTenant(TENANT);

    assertThat(state(solution).getState()).isEqualTo(HealthState.UNKNOWN);
  }

  @Test
  @DisplayName("담당자가 없으면 인프라 관리자에게 떨어진다 — 아무도 모르는 것이 가장 나쁘다")
  void 담당이_없으면_인프라_관리자가_받는다() {
    ManagedUser infra = userService.create(TENANT, new UserForm(
        "infra" + System.nanoTime(), "인프라" + System.nanoTime(),
        "infra" + System.nanoTime() + "@example.com", "safe-password-123", UserStatus.ACTIVE),
        Set.of(UserRole.INFRA_MANAGER));
    // 배정도 소유팀도 없는 솔루션
    ManagedSolution solution = tcpSolution(closedPort());

    healthService.checkTenant(TENANT);
    healthService.checkTenant(TENANT);

    assertThat(titlesFor(infra)).anyMatch(title -> title.startsWith("[장애] " + solution.getName()));
  }

  @Test
  @DisplayName("⚠️ 솔루션 기능을 안 켠 기관은 점검하지 않는다 — 화면도 없는데 알림만 갈 수 없다")
  void 기능을_안_켠_기관은_건너뛴다() {
    Tenant other = tenantService.createTenant(new CreateTenantCommand(
        "헬스미사용-" + System.nanoTime(),
        "H" + Long.toString(System.nanoTime(), 36).toUpperCase()));
    // 신규 기관은 기능 모듈이 전부 꺼진 채로 생성된다.
    assertThat(tenantService.getById(other.getId())
        .hasFeature(com.moara.moa.tenant.FeatureModule.SOLUTIONS)).isFalse();

    Asset server = assetService.create(other.getId(), new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "127.0.0.1", 22, "",
        "LINUX", "t", AssetStatus.ACTIVE));
    ManagedSolution solution = solutionService.create(other.getId(), new SolutionForm(
        server.getId(), "off-tenant", SolutionType.LINUX_DAEMON, "x.service",
        null, HealthCheckType.TCP_PORT, String.valueOf(closedPort()), SolutionStatus.ACTIVE,
        null, null, null, RemoteProtocol.SSH, null));

    healthService.checkAllTenants();
    healthService.checkAllTenants();

    assertThat(healthService.stateOf(other.getId(), solution.getId())).isNull();
  }

  @Test
  @DisplayName("⚠️ 다른 기관의 상태는 보이지 않는다")
  void 상태는_기관을_넘지_않는다() {
    ManagedSolution mine = tcpSolution(closedPort());
    healthService.checkTenant(TENANT);

    Tenant other = tenantService.createTenant(new CreateTenantCommand(
        "타기관-" + System.nanoTime(),
        "X" + Long.toString(System.nanoTime(), 36).toUpperCase()));

    assertThat(healthService.stateOf(other.getId(), mine.getId())).isNull();
    assertThat(healthService.statesOf(other.getId())).doesNotContainKey(mine.getId());
  }

  // ── 도우미 ─────────────────────────────────────────────────────────────

  private SolutionHealth state(ManagedSolution solution) {
    return healthRepository.findByTenantIdAndSolutionId(TENANT, solution.getId()).orElseThrow();
  }

  private List<String> titlesFor(ManagedUser user) {
    return notificationService.list(TENANT, user.getId()).stream()
        .map(Notification::getTitle)
        .toList();
  }

  private long countFor(ManagedUser user) {
    return titlesFor(user).size();
  }

  /** 아무도 듣지 않는 포트. 열었다가 바로 닫아 번호만 얻는다. */
  private int closedPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private ManagedSolution tcpSolution(int port) {
    return solutionService.create(TENANT, new SolutionForm(
        server().getId(), "svc-" + System.nanoTime(), SolutionType.LINUX_DAEMON, "svc.service",
        null, HealthCheckType.TCP_PORT, "127.0.0.1:" + port, SolutionStatus.ACTIVE,
        null, null, null, RemoteProtocol.SSH, null));
  }

  private Asset server() {
    return assetService.create(TENANT, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "127.0.0.1", 22, "",
        "LINUX", "t", AssetStatus.ACTIVE));
  }

  private ManagedUser user() {
    String name = "owner" + System.nanoTime();
    return userService.create(TENANT, new UserForm(
        name, "담당" + System.nanoTime(), name + "@example.com", "safe-password-123",
        UserStatus.ACTIVE), Set.of(UserRole.USER));
  }
}
