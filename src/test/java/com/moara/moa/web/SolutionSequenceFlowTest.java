package com.moara.moa.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetForm;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.credential.Credential;
import com.moara.moa.credential.CredentialForm;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.credential.CredentialType;
import com.moara.moa.remote.ExecResult;
import com.moara.moa.remote.RemoteExecutor;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.solution.ControlAction;
import com.moara.moa.solution.HealthCheckType;
import com.moara.moa.solution.InvalidCronException;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.solution.SequenceRunResult;
import com.moara.moa.solution.SolutionForm;
import com.moara.moa.solution.SolutionSequence;
import com.moara.moa.solution.SolutionSequenceForm;
import com.moara.moa.solution.SequenceScheduler;
import com.moara.moa.solution.SolutionSequenceRepository;
import com.moara.moa.solution.SolutionSequenceRunService;
import com.moara.moa.solution.SolutionSequenceService;
import com.moara.moa.solution.SolutionStatus;
import com.moara.moa.solution.SolutionType;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 기동 순서: per-step 동작 정방향/역방향, 실패 시 멈춤, STATUS 게이팅, cron 검증, 역할 게이팅. */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SolutionSequenceFlowTest {
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @TestConfiguration
  static class StubConfig {
    // 명령에 'FAIL'이 들어가면 실패(exit 1), 아니면 성공(exit 0).
    @Bean(name = "remoteExecutorDispatcher")
    @Primary
    RemoteExecutor stubExecutor() {
      return (target, command) -> new ExecResult(command != null && command.contains("FAIL") ? 1 : 0, "out");
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private SolutionSequenceService sequenceService;
  @Autowired private SolutionSequenceRunService runService;
  @Autowired private ManagedSolutionService solutionService;
  @Autowired private AssetService assetService;
  @Autowired private CredentialService credentialService;
  @Autowired private ManagedUserService userService;
  @Autowired private SequenceScheduler scheduler;
  @Autowired private SolutionSequenceRepository sequenceRepository;

  @Test
  void schedulerFiresDueForwardCron() {
    SolutionSequence seq = sequenceService.create(TENANT, new SolutionSequenceForm("sched-" + System.nanoTime(), null));
    sequenceService.addStep(TENANT, seq.getId(), solution("start.sh", "status.sh").getId(), ControlAction.START, 0, false);
    sequenceService.setSchedule(TENANT, seq.getId(), "0 * * * * *", null); // 매 분 발화

    scheduler.tick();

    SolutionSequence reloaded = sequenceRepository.findById(seq.getId()).orElseThrow();
    assertNotNull(reloaded.getLastForwardFired(), "이번 분 예정 시각에 발화 기록이 남아야 함");
  }

  @Test
  void forwardRunsActionsInOrderReverseInvertsAndReverses() {
    SolutionSequence seq = sequenceService.create(TENANT, new SolutionSequenceForm("seq-" + System.nanoTime(), null));
    ManagedSolution apache = solution("start.sh", "status.sh");
    ManagedSolution tomcat = solution("start.sh", "status.sh");
    // 그룹웨어 중단: 둘 다 STOP.
    sequenceService.addStep(TENANT, seq.getId(), apache.getId(), ControlAction.STOP, 0, false);
    sequenceService.addStep(TENANT, seq.getId(), tomcat.getId(), ControlAction.STOP, 0, false);

    SequenceRunResult forward = runService.run(TENANT, seq.getId(), false);
    assertTrue(forward.overallSuccess());
    assertEquals(apache.getName(), forward.steps().get(0).solutionName());
    assertEquals(ControlAction.STOP, forward.steps().get(0).action());

    // 역방향: 반대 동작(START)을 역순으로 → 첫 결과가 tomcat START.
    SequenceRunResult reverse = runService.run(TENANT, seq.getId(), true);
    assertTrue(reverse.overallSuccess());
    assertEquals(tomcat.getName(), reverse.steps().get(0).solutionName());
    assertEquals(ControlAction.START, reverse.steps().get(0).action());
  }

  @Test
  void failoverMixedActions() {
    SolutionSequence seq = sequenceService.create(TENANT, new SolutionSequenceForm("failover-" + System.nanoTime(), null));
    ManagedSolution serverA = solution("start.sh", "status.sh");
    ManagedSolution serverB = solution("start.sh", "status.sh");
    sequenceService.addStep(TENANT, seq.getId(), serverA.getId(), ControlAction.STOP, 0, false);
    sequenceService.addStep(TENANT, seq.getId(), serverB.getId(), ControlAction.START, 0, false);

    SequenceRunResult result = runService.run(TENANT, seq.getId(), false);
    assertTrue(result.overallSuccess());
    assertEquals(ControlAction.STOP, result.steps().get(0).action());
    assertEquals(ControlAction.START, result.steps().get(1).action());
  }

  @Test
  void haltsOnFailureRemainingSkipped() {
    SolutionSequence seq = sequenceService.create(TENANT, new SolutionSequenceForm("seq-" + System.nanoTime(), null));
    sequenceService.addStep(TENANT, seq.getId(), solution("start.sh", "status.sh").getId(), ControlAction.START, 0, false);
    sequenceService.addStep(TENANT, seq.getId(), solution("FAIL start.sh", "status.sh").getId(), ControlAction.START, 0, false);
    sequenceService.addStep(TENANT, seq.getId(), solution("start.sh", "status.sh").getId(), ControlAction.START, 0, false);

    SequenceRunResult result = runService.run(TENANT, seq.getId(), false);

    assertFalse(result.overallSuccess());
    assertTrue(result.steps().get(0).success());
    assertFalse(result.steps().get(1).success());
    assertTrue(result.steps().get(2).skipped());
  }

  @Test
  void verifyAfterStartGatesOnStatus() {
    SolutionSequence seq = sequenceService.create(TENANT, new SolutionSequenceForm("seq-" + System.nanoTime(), null));
    // START 성공하지만 STATUS 명령은 실패 → 상태확인 켜면 단계 실패.
    sequenceService.addStep(TENANT, seq.getId(),
        solution("start.sh", "FAIL status.sh").getId(), ControlAction.START, 0, true);

    SequenceRunResult result = runService.run(TENANT, seq.getId(), false);

    assertFalse(result.overallSuccess());
    assertFalse(result.steps().get(0).success());
    assertTrue(result.steps().get(0).output().contains("상태 확인 실패"));
  }

  @Test
  void invalidCronRejected() {
    SolutionSequence seq = sequenceService.create(TENANT, new SolutionSequenceForm("seq-" + System.nanoTime(), null));
    assertThrows(InvalidCronException.class,
        () -> sequenceService.setSchedule(TENANT, seq.getId(), "not a cron", null));
    // 정상 cron은 통과.
    sequenceService.setSchedule(TENANT, seq.getId(), "0 0 9 * * *", null);
  }

  @Test
  void sequencesInfraManagerOnly() throws Exception {
    ManagedUser u = user();
    mockMvc.perform(get("/solution-sequences").with(authentication(auth(u, "ROLE_INFRA_MANAGER"))))
        .andExpect(status().isOk());
    mockMvc.perform(get("/solution-sequences").with(authentication(auth(u, "ROLE_USER"))))
        .andExpect(status().isForbidden());
  }

  private ManagedSolution solution(String startCommand, String statusCommand) {
    Asset server = assetService.create(TENANT, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "192.0.2.30", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
    Credential cred = credentialService.create(TENANT, new CredentialForm(
        "c-" + System.nanoTime(), CredentialType.PASSWORD, "mtcm", "test-secret-1"));
    String name = "sol-" + System.nanoTime();
    return solutionService.create(TENANT, new SolutionForm(
        server.getId(), name, SolutionType.CUSTOM_COMMAND, name, cred.getId(),
        HealthCheckType.NONE, null, SolutionStatus.ACTIVE,
        startCommand, "stop.sh", statusCommand, RemoteProtocol.SSH, null));
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }

  private UsernamePasswordAuthenticationToken auth(ManagedUser user, String role) {
    MoaUserDetails principal = new MoaUserDetails(user);
    return new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority(role)));
  }
}
