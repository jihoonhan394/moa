package com.moara.moa.solution;

import com.moara.moa.group.UserGroupMember;
import com.moara.moa.group.UserGroupMemberRepository;
import com.moara.moa.notification.NotificationMailer;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.tenant.FeatureModule;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import com.moara.moa.user.UserStatus;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 솔루션 헬스 체크를 돌리고, 상태가 <b>바뀔 때만</b> 담당자에게 알린다.
 *
 * <p>이 서비스가 생기기 전까지 {@code healthCheckType}·{@code healthCheckTarget} 은 저장과
 * 화면 표시만 있었고 <b>점검하는 코드가 한 줄도 없었다</b>. 그래서 "장애가 나면 폰이 울린다"가
 * 성립하지 않았고, 상태를 보려면 사람이 화면을 열어야 했다.
 *
 * <h2>알림을 줄이는 세 가지 장치</h2>
 *
 * <ol>
 *   <li><b>연속 실패 기준</b>(기본 2회) — 재기동 중 한 번, 네트워크가 순간 끊긴 한 번으로는
 *       알리지 않는다. {@link SolutionHealth#recordFailure} 가 판단한다.
 *   <li><b>상태 전이에만 발송</b> — DOWN 이 지속되는 동안 1분마다 보내지 않는다.
 *       {@code alerted_state} 가 기억한다.
 *   <li><b>UNKNOWN 은 알리지 않는다</b> — 설정 공백은 장애가 아니다.
 * </ol>
 *
 * <h2>트랜잭션을 바깥에 걸지 않는다</h2>
 *
 * {@link com.moara.moa.expiration.ExpirationAlertService} 와 같은 이유다 — SMTP 지연이 DB
 * 커넥션을 붙잡고, 뒤쪽 기관에서 실패하면 앞쪽 기관의 상태 갱신까지 되돌아간다. 배치는
 * 부분 진행이 남는 편이 낫다.
 */
@Service
public class SolutionHealthService {

  private static final Logger log = LoggerFactory.getLogger(SolutionHealthService.class);

  /** 담당자가 아무도 없을 때 떨어뜨릴 수신자. 알림이 사라지는 것이 가장 나쁘다. */
  private static final Set<UserRole> FALLBACK_ROLES =
      Set.of(UserRole.INFRA_MANAGER, UserRole.TENANT_ADMIN);

  private final TenantService tenantService;
  private final ManagedSolutionService solutionService;
  private final SolutionHealthRepository healthRepository;
  private final SolutionHealthProbe probe;
  private final SolutionAccessService accessService;
  private final UserGroupMemberRepository groupMemberRepository;
  private final ManagedUserService userService;
  private final NotificationService notificationService;
  private final NotificationMailer mailer;
  private final int failureThreshold;
  private final Duration commandMinInterval;

  public SolutionHealthService(
      TenantService tenantService, ManagedSolutionService solutionService,
      SolutionHealthRepository healthRepository, SolutionHealthProbe probe,
      SolutionAccessService accessService, UserGroupMemberRepository groupMemberRepository,
      ManagedUserService userService, NotificationService notificationService,
      NotificationMailer mailer,
      @Value("${moa.solution.health-failure-threshold:2}") int failureThreshold,
      @Value("${moa.solution.health-command-min-interval-ms:300000}") long commandMinIntervalMs) {
    this.tenantService = tenantService;
    this.solutionService = solutionService;
    this.healthRepository = healthRepository;
    this.probe = probe;
    this.accessService = accessService;
    this.groupMemberRepository = groupMemberRepository;
    this.userService = userService;
    this.notificationService = notificationService;
    this.mailer = mailer;
    this.failureThreshold = Math.max(1, failureThreshold);
    this.commandMinInterval = Duration.ofMillis(Math.max(0, commandMinIntervalMs));
  }

  /** 전 기관 순회. 반환값은 보낸 알림 묶음 수(상태 전이 건수)다. */
  public int checkAllTenants() {
    int alerts = 0;
    for (Tenant tenant : tenantService.findAll()) {
      // 솔루션 기능을 안 켠 기관은 점검하지 않는다. 화면도 없는데 알림만 가면 안 된다.
      if (!tenant.hasFeature(FeatureModule.SOLUTIONS)) {
        continue;
      }
      try {
        alerts += checkTenant(tenant.getId());
      } catch (RuntimeException exception) {
        // 한 기관에서 터져도 나머지 기관은 점검해야 한다.
        log.warn("[HEALTH] 기관 {} 점검 실패: {}", tenant.getId(), exception.toString());
      }
    }
    return alerts;
  }

  /**
   * 한 기관의 솔루션을 점검한다. 조회·발송 모두 이 기관으로 스코프된다.
   *
   * @return 상태가 바뀌어 알림을 보낸 솔루션 수
   */
  public int checkTenant(UUID tenantId) {
    List<ManagedSolution> solutions = solutionService.findAll(tenantId);
    if (solutions.isEmpty()) {
      return 0;
    }
    Map<UUID, SolutionHealth> known = healthRepository.findAllByTenantId(tenantId).stream()
        .collect(Collectors.toMap(SolutionHealth::getSolutionId, Function.identity(), (a, b) -> a));
    List<ManagedUser> tenantUsers = userService.findByTenant(tenantId);
    OffsetDateTime now = OffsetDateTime.now();

    // 이번 실행에서 상태가 바뀐 것만 모아 사람당 메일 한 통으로 보낸다. 장애가 6건이면
    // 인앱 알림 6건은 맞지만 메일 6통이면 수신자가 메일을 차단한다.
    Map<String, List<String>> digest = new LinkedHashMap<>();
    int alerts = 0;

    for (ManagedSolution solution : solutions) {
      // 꺼 둔 솔루션(DISABLED)은 점검하지 않는다 — 안 돌리기로 한 것이 안 도는 건 장애가 아니다.
      if (solution.getStatus() != SolutionStatus.ACTIVE) {
        continue;
      }
      SolutionHealth health = known.get(solution.getId());
      if (health == null) {
        health = SolutionHealth.initial(solution.getId(), tenantId);
      }

      if (solution.getHealthCheckType() == HealthCheckType.NONE) {
        // 점검을 끈 솔루션에 지난 상태가 남아 있으면 화면이 과거의 장애를 계속 보여 준다.
        if (health.getState() != HealthState.UNKNOWN) {
          health.recordUnknown("헬스 체크를 사용하지 않습니다.", now);
          healthRepository.save(health);
        }
        continue;
      }
      if (!dueNow(solution, health, now)) {
        continue;
      }

      SolutionHealthProbe.Result result;
      try {
        result = probe.probe(solution);
      } catch (RuntimeException exception) {
        // 점검 중 예외는 솔루션 하나의 문제다. 여기서 삼키지 않으면 뒤 솔루션이 전부 안 돈다.
        log.warn("[HEALTH] 솔루션 {} 점검 중 예외: {}", solution.getId(), exception.toString());
        result = new SolutionHealthProbe.Result(HealthState.DOWN, "점검 중 오류가 발생했습니다.");
      }

      switch (result.state()) {
        case UP -> health.recordSuccess(now);
        case DOWN -> health.recordFailure(result.detail(), now, failureThreshold);
        case UNKNOWN -> health.recordUnknown(result.detail(), now);
      }
      // 알림을 보낸 뒤에 한 번만 저장한다. 보내기 전에 저장하고 또 저장하면 UPDATE 가 두 번
      // 나가고, 그 사이에 "알렸다"가 기록되지 않은 창이 생긴다.
      if (health.needsAlert() && alert(tenantId, solution, health, tenantUsers, digest)) {
        // ⚠️ 발송이 실제로 된 경우에만 표시한다. 받을 사람이 없어 못 보낸 것을 "알렸다"로
        // 적으면, 나중에 담당자가 배정돼도 그 장애는 영원히 통보되지 않는다(화면에만 남는다).
        health.markAlerted();
        alerts++;
      }
      healthRepository.save(health);
    }

    if (!digest.isEmpty()) {
      mailer.sendDigest(tenantId, digest, "[MOA] 솔루션 상태 변경 %d건",
          "솔루션 상태가 바뀌었습니다.", "\n", "/solutions");
    }
    return alerts;
  }

  /** 지금 점검할 차례인가. COMMAND 만 간격을 둔다 — 이유는 {@link #commandMinInterval} 주석. */
  private boolean dueNow(ManagedSolution solution, SolutionHealth health, OffsetDateTime now) {
    if (solution.getHealthCheckType() != HealthCheckType.COMMAND) {
      return true;
    }
    // COMMAND 점검은 매번 SSH 세션을 연다. 솔루션 24개를 1분마다 찌르면 대상 서버의 인증
    // 로그가 분당 24줄씩 쌓이고, 그 자체로 운영에 부담이다. TCP 는 소켓 하나라 매번 돌린다.
    OffsetDateTime last = health.getCheckedAt();
    return last == null || !last.plus(commandMinInterval).isAfter(now);
  }

  /** @return 인앱 알림을 한 명 이상에게 보냈으면 true */
  private boolean alert(UUID tenantId, ManagedSolution solution, SolutionHealth health,
      List<ManagedUser> tenantUsers, Map<String, List<String>> digest) {
    List<ManagedUser> recipients = recipients(tenantId, solution, tenantUsers);
    if (recipients.isEmpty()) {
      log.warn("[HEALTH] 솔루션 {} 상태 {} — 받을 사람이 없다", solution.getName(), health.getState());
      return false;
    }
    boolean down = health.getState() == HealthState.DOWN;
    String title = (down ? "[장애] " : "[복구] ") + solution.getName();
    String body = down
        ? (health.getDetail() == null ? "응답하지 않습니다." : health.getDetail())
        : "정상으로 돌아왔습니다.";
    // 링크는 PC 경로다. 모바일 알림 화면이 /m/** 로 바꿔 띄운다(MobileLinks).
    String link = "/solutions/" + solution.getId();
    for (ManagedUser recipient : recipients) {
      notificationService.notify(tenantId, recipient.getId(), title, body, link);
      digest.computeIfAbsent(recipient.getEmail(), key -> new ArrayList<>()).add(title + " — " + body);
    }
    log.info("[HEALTH] {} -> {} ({}명에게 알림)", solution.getName(), health.getState(), recipients.size());
    return true;
  }

  /**
   * 누가 받나: <b>개인 배정자 + 소유팀 팀원</b>. 둘 다 없으면 인프라 관리자·기관 관리자로
   * 떨어뜨린다 — 담당이 지정되지 않은 솔루션의 장애를 아무도 모르는 것이 가장 나쁘다.
   *
   * <p>퇴사·정지 계정은 제외한다. 받을 수 없는 사람에게 보낸 알림은 안 보낸 것과 같으면서
   * 보낸 것처럼 보인다.
   */
  private List<ManagedUser> recipients(
      UUID tenantId, ManagedSolution solution, List<ManagedUser> tenantUsers) {
    Set<UUID> ids = new LinkedHashSet<>(accessService.assignedUserIds(tenantId, solution.getId()));
    if (solution.getOwnerGroupId() != null) {
      groupMemberRepository.findAllByTenantIdAndGroupId(tenantId, solution.getOwnerGroupId())
          .stream().map(UserGroupMember::getUserId).forEach(ids::add);
    }
    List<ManagedUser> active = tenantUsers.stream()
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .toList();
    List<ManagedUser> assigned = active.stream()
        .filter(user -> ids.contains(user.getId()))
        .toList();
    if (!assigned.isEmpty()) {
      return assigned;
    }
    return active.stream()
        .filter(user -> FALLBACK_ROLES.stream().anyMatch(user::hasRole))
        .toList();
  }

  /** 화면용: 솔루션 → 상태. 솔루션마다 단건 조회하면 목록에서 질의가 N 번 나간다. */
  @Transactional(readOnly = true)
  public Map<UUID, SolutionHealth> statesOf(UUID tenantId) {
    return healthRepository.findAllByTenantId(tenantId).stream()
        .collect(Collectors.toMap(SolutionHealth::getSolutionId, Function.identity(), (a, b) -> a));
  }

  @Transactional(readOnly = true)
  public SolutionHealth stateOf(UUID tenantId, UUID solutionId) {
    return healthRepository.findByTenantIdAndSolutionId(tenantId, solutionId).orElse(null);
  }

  /** 장애 건수. 모바일 홈·솔루션 탭의 뱃지에 쓴다. */
  @Transactional(readOnly = true)
  public long downCount(UUID tenantId) {
    return healthRepository.countByTenantIdAndState(tenantId, HealthState.DOWN);
  }
}
