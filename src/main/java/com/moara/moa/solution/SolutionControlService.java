package com.moara.moa.solution;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.remote.ExecResult;
import com.moara.moa.remote.RemoteExecutor;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.remote.RemoteTarget;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 솔루션 제어(start/stop/restart/status)를 오케스트레이션한다.
 * 솔루션 정의 + 자산(호스트/포트) + 볼트 자격증명으로 원격 명령을 만들어 {@link RemoteExecutor}로 실행한다.
 * 명령은 유형별로 매핑한다(systemd/docker/custom/windows). 자격증명 평문은 즉시 사용·폐기하며 로그에 남기지 않는다.
 * 윈도우는 대상 호스트의 OpenSSH Server를 통해 PowerShell 명령을 실행한다.
 * 원격 명령에 끼워넣는 식별자는 화이트리스트로 검증해 명령 인젝션을 방지한다.
 */
@Service
public class SolutionControlService {
  private final ManagedSolutionService solutionService;
  private final AssetService assetService;
  private final CredentialService credentialService;
  private final RemoteExecutor remoteExecutor;

  public SolutionControlService(
      ManagedSolutionService solutionService, AssetService assetService,
      CredentialService credentialService, RemoteExecutor remoteExecutor) {
    this.solutionService = solutionService;
    this.assetService = assetService;
    this.credentialService = credentialService;
    this.remoteExecutor = remoteExecutor;
  }

  @Transactional(readOnly = true)
  public ControlResult control(UUID tenantId, UUID solutionId, ControlAction action) {
    ManagedSolution solution = solutionService.findById(tenantId, solutionId);
    if (solution.getCredentialId() == null) {
      throw new ControlNotSupportedException("제어 자격증명이 설정되지 않았습니다.");
    }
    Asset asset = assetService.findById(tenantId, solution.getAssetId());
    CredentialService.ResolvedCredential credential =
        credentialService.resolveSecret(tenantId, solution.getCredentialId());
    String command = buildCommand(solution, action);
    RemoteTarget target = new RemoteTarget(
        tenantId, asset.getHost(), controlPort(solution, asset.getPort()), credential.username(),
        credential.secret(), controlProtocol(solution));
    ExecResult result = remoteExecutor.execute(target, command);
    return new ControlResult(action, result.success(), result.output());
  }

  /**
   * 솔루션의 로그 수집 명령(log_command)을 제어와 동일 채널(원격)으로 실행해 원시 로그를 가져온다.
   * 온디맨드 분석용 — 실시간 아님. 명령은 관리자(INFRA)가 설정한 자기 서버 명령이라 자유 문자열(제어 명령과 동일 신뢰).
   */
  @Transactional(readOnly = true)
  public String fetchLogRaw(UUID tenantId, UUID solutionId) {
    ManagedSolution solution = solutionService.findById(tenantId, solutionId);
    String logCommand = solution.getLogCommand();
    if (logCommand == null || logCommand.isBlank()) {
      throw new ControlNotSupportedException("로그 수집 명령이 설정되지 않았습니다.");
    }
    if (solution.getCredentialId() == null) {
      throw new ControlNotSupportedException("제어 자격증명이 설정되지 않았습니다.");
    }
    Asset asset = assetService.findById(tenantId, solution.getAssetId());
    CredentialService.ResolvedCredential credential =
        credentialService.resolveSecret(tenantId, solution.getCredentialId());
    RemoteTarget target = new RemoteTarget(
        tenantId, asset.getHost(), controlPort(solution, asset.getPort()), credential.username(),
        credential.secret(), controlProtocol(solution));
    return remoteExecutor.execute(target, logCommand).output();
  }

  private static final int DEFAULT_WINRM_HTTP_PORT = 5985;

  private static RemoteProtocol controlProtocol(ManagedSolution solution) {
    return solution.getControlProtocol() == null ? RemoteProtocol.SSH : solution.getControlProtocol();
  }

  /** 제어 포트: 명시값 우선, 없으면 프로토콜별 기본(WinRM=5985, SSH=자산 포트). */
  private static int controlPort(ManagedSolution solution, int assetPort) {
    if (solution.getControlPort() != null) {
      return solution.getControlPort();
    }
    return controlProtocol(solution) == RemoteProtocol.WINRM ? DEFAULT_WINRM_HTTP_PORT : assetPort;
  }

  /** 유형 + 액션 → 원격 명령 문자열. */
  static String buildCommand(ManagedSolution solution, ControlAction action) {
    return switch (solution.getType()) {
      case LINUX_DAEMON -> "sudo systemctl " + linuxVerb(action) + " " + safeId(solution.getIdentifier());
      case DOCKER_CONTAINER -> action == ControlAction.STATUS
          ? "docker inspect -f '{{.State.Status}}' " + safeId(solution.getIdentifier())
          : "docker " + dockerVerb(action) + " " + safeId(solution.getIdentifier());
      case CUSTOM_COMMAND -> customCommand(solution, action);
      case WINDOWS_SERVICE -> windowsServiceCommand(solution, action);
      case WINDOWS_EXE -> windowsExeCommand(solution, action);
    };
  }

  /** 원격 명령에 끼워넣을 식별자를 화이트리스트로 검증한다(명령 인젝션 방지). */
  private static String safeId(String identifier) {
    if (identifier == null || !identifier.matches("[A-Za-z0-9._@:-]+")) {
      throw new ControlNotSupportedException("허용되지 않은 식별자입니다: " + identifier);
    }
    return identifier;
  }

  /** Windows 서비스 제어(대상 호스트 OpenSSH → PowerShell). */
  private static String windowsServiceCommand(ManagedSolution solution, ControlAction action) {
    String name = safeId(solution.getIdentifier());
    String script = switch (action) {
      case START -> "Start-Service -Name '" + name + "'";
      case STOP -> "Stop-Service -Name '" + name + "' -Force";
      case RESTART -> "Restart-Service -Name '" + name + "' -Force";
      case STATUS -> "(Get-Service -Name '" + name + "').Status";
    };
    return powershell(script);
  }

  /**
   * Windows 실행파일(프로세스) 제어. 식별자는 프로세스 이미지명, 실행 경로는 startCommand.
   * START는 WMI(Win32_Process.Create)로 띄운다 — SSH 세션 job object 밖에서 생성되어
   * 제어 명령/세션 종료 뒤에도 프로세스가 유지된다(Start-Process는 세션 종료 시 함께 종료됨).
   */
  private static String windowsExeCommand(ManagedSolution solution, ControlAction action) {
    String name = safeId(solution.getIdentifier());
    String stop = "Stop-Process -Name '" + name + "' -Force -ErrorAction SilentlyContinue";
    String start = "Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{CommandLine="
        + exePath(solution) + "} | Out-Null";
    String script = switch (action) {
      case START -> start;
      case STOP -> stop;
      case RESTART -> stop + "; " + start;
      case STATUS -> "if (Get-Process -Name '" + name
          + "' -ErrorAction SilentlyContinue) { 'running' } else { 'stopped' }";
    };
    return powershell(script);
  }

  /** WINDOWS_EXE 실행 경로(startCommand)를 PowerShell 리터럴로 감싼다. 따옴표/백틱은 거부한다. */
  private static String exePath(ManagedSolution solution) {
    String path = solution.getStartCommand();
    if (path == null || path.isBlank()) {
      throw new ControlNotSupportedException("WINDOWS_EXE START에는 실행 경로(startCommand)가 필요합니다.");
    }
    path = path.trim();
    if (path.contains("'") || path.contains("\"") || path.contains("`")) {
      throw new ControlNotSupportedException("허용되지 않은 실행 경로입니다.");
    }
    return "'" + path + "'";
  }

  /** PowerShell 스크립트를 원격 실행 명령으로 감싼다(Windows OpenSSH 기본 셸이 cmd이므로 명시 호출). */
  private static String powershell(String script) {
    return "powershell -NoProfile -NonInteractive -Command \"" + script + "\"";
  }

  private static String linuxVerb(ControlAction action) {
    return switch (action) {
      case START -> "start";
      case STOP -> "stop";
      case RESTART -> "restart";
      case STATUS -> "is-active";
    };
  }

  private static String dockerVerb(ControlAction action) {
    return switch (action) {
      case START -> "start";
      case STOP -> "stop";
      case RESTART -> "restart";
      case STATUS -> "inspect"; // STATUS는 buildCommand에서 별도 처리(도달하지 않음)
    };
  }

  private static String customCommand(ManagedSolution solution, ControlAction action) {
    String command = switch (action) {
      case START -> solution.getStartCommand();
      case STOP -> solution.getStopCommand();
      case STATUS -> solution.getStatusCommand();
      case RESTART -> hasBoth(solution) ? solution.getStopCommand() + " ; " + solution.getStartCommand() : null;
    };
    if (command == null || command.isBlank()) {
      throw new ControlNotSupportedException("이 액션에 대한 명령이 설정되지 않았습니다: " + action);
    }
    return command;
  }

  private static boolean hasBoth(ManagedSolution solution) {
    return solution.getStartCommand() != null && !solution.getStartCommand().isBlank()
        && solution.getStopCommand() != null && !solution.getStopCommand().isBlank();
  }
}
