package com.moara.moa.solution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.moara.moa.remote.RemoteTarget;
import com.moara.moa.tenant.Tenant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@ActiveProfiles("test")
class SolutionControlServiceTest {
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @TestConfiguration
  static class StubConfig {
    // 실행기 디스패처(@Primary)를 기록용 스텁으로 대체해 실제 네트워크 없이 대상/명령을 검증한다.
    @Bean(name = "remoteExecutorDispatcher")
    @Primary
    RecordingExecutor recordingExecutor() {
      return new RecordingExecutor();
    }
  }

  static class RecordingExecutor implements RemoteExecutor {
    RemoteTarget lastTarget;
    String lastCommand;

    @Override
    public ExecResult execute(RemoteTarget target, String command) {
      this.lastTarget = target;
      this.lastCommand = command;
      return new ExecResult(0, "active");
    }
  }

  @Autowired private SolutionControlService controlService;
  @Autowired private RecordingExecutor executor;
  @Autowired private ManagedSolutionService solutionService;
  @Autowired private AssetService assetService;
  @Autowired private CredentialService credentialService;

  @Test
  void controlResolvesCredentialAndSendsLinuxCommand() {
    Asset server = assetService.create(TENANT, new AssetForm(
        "srv-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.SSH, "100.85.241.103", 22, "", "LINUX", "t",
        AssetStatus.ACTIVE));
    Credential credential = credentialService.create(TENANT, new CredentialForm(
        "c-" + System.nanoTime(), CredentialType.PASSWORD, "mtcm", "test-secret-1"));
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "testapp", SolutionType.LINUX_DAEMON, "testapp",
        credential.getId(), HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null,
        RemoteProtocol.SSH, null));

    ControlResult result = controlService.control(TENANT, solution.getId(), ControlAction.START);

    assertTrue(result.success());
    assertEquals("sudo systemctl start testapp", executor.lastCommand);
    assertEquals("mtcm", executor.lastTarget.username());
    assertEquals("test-secret-1", executor.lastTarget.secret()); // 볼트 복호화 라운드트립
    assertEquals(22, executor.lastTarget.port());
    assertEquals("100.85.241.103", executor.lastTarget.host());
  }

  @Test
  void buildsCommandsPerType() {
    assertEquals("sudo systemctl is-active unit",
        SolutionControlService.buildCommand(solution(SolutionType.LINUX_DAEMON, "unit"), ControlAction.STATUS));
    assertEquals("docker start web",
        SolutionControlService.buildCommand(solution(SolutionType.DOCKER_CONTAINER, "web"), ControlAction.START));
    assertEquals("docker inspect -f '{{.State.Status}}' web",
        SolutionControlService.buildCommand(solution(SolutionType.DOCKER_CONTAINER, "web"), ControlAction.STATUS));
    assertEquals("powershell -NoProfile -NonInteractive -Command \"Start-Service -Name 'svc'\"",
        SolutionControlService.buildCommand(solution(SolutionType.WINDOWS_SERVICE, "svc"), ControlAction.START));
    assertEquals("powershell -NoProfile -NonInteractive -Command \"(Get-Service -Name 'svc').Status\"",
        SolutionControlService.buildCommand(solution(SolutionType.WINDOWS_SERVICE, "svc"), ControlAction.STATUS));
  }

  @Test
  void rejectsUnsafeIdentifier() {
    assertThrows(ControlNotSupportedException.class,
        () -> SolutionControlService.buildCommand(
            solution(SolutionType.LINUX_DAEMON, "unit; rm -rf /"), ControlAction.START));
    assertThrows(ControlNotSupportedException.class,
        () -> SolutionControlService.buildCommand(
            solution(SolutionType.WINDOWS_SERVICE, "svc' ; Stop-Computer #"), ControlAction.STOP));
  }

  @Test
  void buildsWindowsExeCommands() {
    ManagedSolution exe = new ManagedSolution(UUID.randomUUID(), TENANT, new SolutionForm(
        UUID.randomUUID(), "app", SolutionType.WINDOWS_EXE, "app", null, HealthCheckType.NONE, null,
        SolutionStatus.ACTIVE, "C:\\apps\\app.exe", null, null, RemoteProtocol.SSH, null), OffsetDateTime.now());
    assertEquals(
        "powershell -NoProfile -NonInteractive -Command \"Invoke-CimMethod -ClassName Win32_Process"
            + " -MethodName Create -Arguments @{CommandLine='C:\\apps\\app.exe'} | Out-Null\"",
        SolutionControlService.buildCommand(exe, ControlAction.START));
    assertEquals(
        "powershell -NoProfile -NonInteractive -Command \"if (Get-Process -Name 'app'"
            + " -ErrorAction SilentlyContinue) { 'running' } else { 'stopped' }\"",
        SolutionControlService.buildCommand(exe, ControlAction.STATUS));
    assertEquals(
        "powershell -NoProfile -NonInteractive -Command"
            + " \"Stop-Process -Name 'app' -Force -ErrorAction SilentlyContinue\"",
        SolutionControlService.buildCommand(exe, ControlAction.STOP));
  }

  @Test
  void customCommandUsesConfiguredCommand() {
    ManagedSolution custom = new ManagedSolution(UUID.randomUUID(), TENANT, new SolutionForm(
        UUID.randomUUID(), "app", SolutionType.CUSTOM_COMMAND, "app", null, HealthCheckType.NONE, null,
        SolutionStatus.ACTIVE, "bash start.sh", "bash stop.sh", "bash status.sh",
        RemoteProtocol.SSH, null), OffsetDateTime.now());
    assertEquals("bash start.sh", SolutionControlService.buildCommand(custom, ControlAction.START));
    assertEquals("bash stop.sh", SolutionControlService.buildCommand(custom, ControlAction.STOP));
    assertEquals("bash status.sh", SolutionControlService.buildCommand(custom, ControlAction.STATUS));
  }

  @Test
  void winRmSolutionRoutesToWinRmChannelWithDefaultPort() {
    Asset server = assetService.create(TENANT, new AssetForm(
        "win-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.RDP, "100.101.19.11", 3389, "", "WINDOWS", "t",
        AssetStatus.ACTIVE));
    Credential credential = credentialService.create(TENANT, new CredentialForm(
        "wc-" + System.nanoTime(), CredentialType.PASSWORD, "MTCM", "P@ss"));
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "wintime", SolutionType.WINDOWS_SERVICE, "W32Time",
        credential.getId(), HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null,
        RemoteProtocol.WINRM, null));

    controlService.control(TENANT, solution.getId(), ControlAction.STATUS);

    assertEquals(RemoteProtocol.WINRM, executor.lastTarget.protocol());
    assertEquals(5985, executor.lastTarget.port()); // WinRM 기본(자산 포트 3389 무시)
    assertEquals("powershell -NoProfile -NonInteractive -Command \"(Get-Service -Name 'W32Time').Status\"",
        executor.lastCommand);
  }

  @Test
  void explicitControlPortOverridesDefault() {
    Asset server = assetService.create(TENANT, new AssetForm(
        "win2-" + System.nanoTime(), AssetType.SERVER, AssetProtocol.RDP, "100.101.19.11", 3389, "", "WINDOWS", "t",
        AssetStatus.ACTIVE));
    Credential credential = credentialService.create(TENANT, new CredentialForm(
        "wc2-" + System.nanoTime(), CredentialType.PASSWORD, "MTCM", "P@ss"));
    ManagedSolution solution = solutionService.create(TENANT, new SolutionForm(
        server.getId(), "wintime2", SolutionType.WINDOWS_SERVICE, "W32Time",
        credential.getId(), HealthCheckType.NONE, null, SolutionStatus.ACTIVE, null, null, null,
        RemoteProtocol.WINRM, 5986));

    controlService.control(TENANT, solution.getId(), ControlAction.STATUS);

    assertEquals(5986, executor.lastTarget.port());
  }

  private ManagedSolution solution(SolutionType type, String identifier) {
    return new ManagedSolution(UUID.randomUUID(), TENANT, new SolutionForm(
        UUID.randomUUID(), "n", type, identifier, null, HealthCheckType.NONE, null, SolutionStatus.ACTIVE,
        null, null, null, RemoteProtocol.SSH, null), OffsetDateTime.now());
  }
}
