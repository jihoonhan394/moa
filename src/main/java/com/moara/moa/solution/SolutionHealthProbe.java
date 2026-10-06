package com.moara.moa.solution;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.remote.ExecResult;
import com.moara.moa.remote.RemoteExecutor;
import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.remote.RemoteTarget;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 솔루션 하나를 실제로 찔러 보고 살았는지 판정한다. 상태 저장·알림은 하지 않는다 —
 * 판정만 돌려준다({@link SolutionHealthService} 가 쓴다).
 *
 * <h2>왜 UNKNOWN 을 따로 돌려주나</h2>
 *
 * 포트를 안 적었거나 자격증명이 없으면 <b>점검이 불가능</b>한 것이고, 그건 장애가 아니라
 * 설정 공백이다. 그것까지 DOWN 으로 올리면 설정이 덜 된 솔루션 때문에 담당자가 새벽에 깨고,
 * 그 다음부터 알림을 끈다.
 *
 * <h2>⚠️ COMMAND 점검은 볼트를 연다</h2>
 *
 * {@code CredentialService#resolveSecret} 를 배치에서 부른다. 두 가지가 걸려 있다.
 *
 * <ul>
 *   <li>행위자가 없는 시스템 맥락이므로 {@code TenantAuditRecorder} 는 아무 것도 남기지
 *       않는다(의도된 정책 — 행위자 없는 감사 기록은 나중에 어떤 질문에도 답하지 못한다).
 *       누가 열었는지가 아니라 <b>배치가 열었다</b>는 사실은 로그로만 남는다.
 *   <li><b>P6/P14 의 step-up 게이트를 {@code resolveSecret} 에 걸 때 이 경로를 반드시
 *       예외로 둬야 한다.</b> 배치는 2차 인증을 할 수 없다. 그냥 걸면 COMMAND 점검이
 *       전부 조용히 실패하면서 "장애 아님"이 "장애"로 바뀐다.
 * </ul>
 */
@Component
public class SolutionHealthProbe {

  private static final int DEFAULT_WINRM_HTTP_PORT = 5985;

  /** 접속 타임아웃의 하한. 0 은 "무한 대기"라서 설정값으로 받아선 안 된다. */
  private static final int MIN_CONNECT_TIMEOUT_MS = 100;

  private final AssetService assetService;
  private final CredentialService credentialService;
  private final RemoteExecutor remoteExecutor;
  private final int connectTimeoutMs;

  public SolutionHealthProbe(
      AssetService assetService, CredentialService credentialService, RemoteExecutor remoteExecutor,
      @Value("${moa.solution.health-connect-timeout-ms:2000}") int connectTimeoutMs) {
    this.assetService = assetService;
    this.credentialService = credentialService;
    this.remoteExecutor = remoteExecutor;
    // ⚠️ 0 이나 음수를 그대로 쓰면 안 된다. Socket.connect(addr, 0) 은 "타임아웃 없음"이라
    // 응답 없는 호스트에서 **영원히 블록**하고, 배치는 fixedDelay 라 그 뒤로 다시 돌지 않는다.
    // 헬스 체크가 멈춘 것을 아무도 모르는 채 화면은 마지막 상태를 계속 보여 준다.
    this.connectTimeoutMs = Math.max(MIN_CONNECT_TIMEOUT_MS, connectTimeoutMs);
  }

  /** 판정 결과. {@code detail} 은 사람이 읽을 사유(화면·알림 본문). */
  public record Result(HealthState state, String detail) {
    static Result up() {
      return new Result(HealthState.UP, null);
    }

    static Result down(String detail) {
      return new Result(HealthState.DOWN, detail);
    }

    static Result unknown(String detail) {
      return new Result(HealthState.UNKNOWN, detail);
    }
  }

  public Result probe(ManagedSolution solution) {
    Asset asset;
    try {
      asset = assetService.findById(solution.getTenantId(), solution.getAssetId());
    } catch (RuntimeException exception) {
      return Result.unknown("서버 자산을 찾을 수 없습니다.");
    }
    return switch (solution.getHealthCheckType()) {
      case NONE -> Result.unknown(null);
      case TCP_PORT -> probeTcp(solution, asset);
      case COMMAND -> probeCommand(solution, asset);
    };
  }

  /**
   * 포트가 열려 있나. 대상은 {@code healthCheckTarget} 이고 세 가지 형태를 받는다 —
   * {@code 8080}(자산 호스트의 포트), {@code host:8080}, 비어 있으면 자산의 포트.
   */
  private Result probeTcp(ManagedSolution solution, Asset asset) {
    String target = solution.getHealthCheckTarget();
    String host = asset.getHost();
    Integer port = asset.getPort();

    if (target != null && !target.isBlank()) {
      String trimmed = target.trim();
      int colon = trimmed.lastIndexOf(':');
      if (colon > 0) {
        host = trimmed.substring(0, colon).trim();
        port = parsePort(trimmed.substring(colon + 1));
      } else {
        port = parsePort(trimmed);
      }
      if (port == null) {
        return Result.unknown("health 대상이 포트 번호가 아닙니다: " + trimmed);
      }
    }
    if (host == null || host.isBlank()) {
      return Result.unknown("서버 호스트가 비어 있습니다.");
    }
    if (port == null) {
      return Result.unknown("점검할 포트가 없습니다(health 대상 또는 자산 포트를 지정하세요).");
    }

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
      return Result.up();
    } catch (UnknownHostException exception) {
      // 설정 오류로 보이지만 DNS 장애일 수도 있다. 어느 쪽이든 "서비스에 닿지 못했다"는
      // 사실은 같고, 담당자가 지금 봐야 하는 일이다.
      return Result.down("호스트 이름을 찾을 수 없습니다: " + host);
    } catch (IOException exception) {
      return Result.down("포트 " + port + " 에 접속하지 못했습니다.");
    }
  }

  /**
   * 상태 확인 명령의 종료코드가 0 인가. 명령은 {@code healthCheckTarget} 우선, 없으면
   * {@code statusCommand} 를 쓴다(제어 화면에서 이미 쓰는 그 명령이다).
   */
  private Result probeCommand(ManagedSolution solution, Asset asset) {
    String command = firstNonBlank(solution.getHealthCheckTarget(), solution.getStatusCommand());
    if (command == null) {
      return Result.unknown("점검 명령이 없습니다(health 대상 또는 status 명령을 지정하세요).");
    }
    if (solution.getCredentialId() == null) {
      return Result.unknown("제어 자격증명이 설정되지 않아 점검할 수 없습니다.");
    }
    if (asset.getHost() == null || asset.getHost().isBlank()) {
      return Result.unknown("서버 호스트가 비어 있습니다.");
    }
    try {
      CredentialService.ResolvedCredential credential =
          credentialService.resolveSecret(solution.getTenantId(), solution.getCredentialId());
      RemoteProtocol protocol =
          solution.getControlProtocol() == null ? RemoteProtocol.SSH : solution.getControlProtocol();
      int port = solution.getControlPort() != null ? solution.getControlPort()
          : protocol == RemoteProtocol.WINRM ? DEFAULT_WINRM_HTTP_PORT
          : asset.getPort() == null ? 22 : asset.getPort();
      RemoteTarget target = new RemoteTarget(
          solution.getTenantId(), asset.getHost(), port,
          credential.username(), credential.secret(), protocol);
      ExecResult result = remoteExecutor.execute(target, command);
      return result.success() ? Result.up() : Result.down("점검 명령이 실패했습니다(종료코드 "
          + result.exitCode() + ").");
    } catch (RuntimeException exception) {
      // 접속 자체가 안 된 것(호스트 다운·인증 실패·볼트 오류)을 구별하지 않는다. 명령의
      // 출력이나 예외 메시지를 그대로 담으면 자격증명·경로가 알림과 화면으로 새어 나간다.
      return Result.down("점검 명령을 실행하지 못했습니다.");
    }
  }

  private static Integer parsePort(String value) {
    try {
      int port = Integer.parseInt(value.trim());
      return port >= 1 && port <= 65535 ? port : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first.trim();
    }
    return second != null && !second.isBlank() ? second.trim() : null;
  }
}
