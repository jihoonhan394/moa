package com.moara.moa.connection;

import com.moara.moa.access.AccessApprovalService;
import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.guacamole.GuacamoleClient;
import com.moara.moa.guacamole.GuacamoleConnectionRequest;
import com.moara.moa.guacamole.GuacamoleException;
import com.moara.moa.guacamole.GuacamoleLaunch;
import com.moara.moa.permission.AccessibleAssetService;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 서버 자산 웹 접속(SSH/RDP) 오케스트레이션. 설계 흐름을 그대로 따른다:
 * 권한 검사 → 접속 시도 기록 → Guacamole 1회성 세션 생성 → 성공/실패 기록.
 * 자격증명은 접속 시점 입력값으로 Guacamole 토큰에만 실리고 저장되지 않는다.
 */
@Service
public class ConnectionLaunchService {
  private final AccessibleAssetService accessibleAssetService;
  private final AssetService assetService;
  private final ConnectionSessionService sessionService;
  private final GuacamoleClient guacamoleClient;
  private final AccessApprovalService approvalService;

  public ConnectionLaunchService(
      AccessibleAssetService accessibleAssetService,
      AssetService assetService,
      ConnectionSessionService sessionService,
      GuacamoleClient guacamoleClient,
      AccessApprovalService approvalService) {
    this.accessibleAssetService = accessibleAssetService;
    this.assetService = assetService;
    this.sessionService = sessionService;
    this.guacamoleClient = guacamoleClient;
    this.approvalService = approvalService;
  }

  public boolean isEnabled() {
    return guacamoleClient.isEnabled();
  }

  /**
   * 접속을 시작하고 Guacamole 접속 URL을 돌려준다. 권한이 없으면 시도를 FAILED로 기록하고
   * {@link ConnectionNotAllowedException}을 던진다. 자산은 SSH/RDP 서버여야 한다.
   */
  public String launch(
      UUID tenantId, UUID userId, UUID assetId, String username, String password, String clientIp) {
    return launch(tenantId, userId, assetId, username, password, clientIp, true);
  }

  /**
   * 인프라 관리자용 접속. 라우트(/servers/**)가 이미 INFRA_MANAGER로 게이트되므로 사용자별 접근권한
   * 부여 없이도 자기 기관 서버에 접속할 수 있게 한다(관리 대상 = 접속 가능). 나머지 흐름은 동일.
   */
  public String launchAsManager(
      UUID tenantId, UUID userId, UUID assetId, String username, String password, String clientIp) {
    return launch(tenantId, userId, assetId, username, password, clientIp, false);
  }

  private String launch(
      UUID tenantId, UUID userId, UUID assetId, String username, String password, String clientIp,
      boolean requireGrant) {
    Asset asset = assetService.findById(tenantId, assetId); // 소유권(타 테넌트 NotFound)
    SessionProtocol protocol = toSessionProtocol(asset.getProtocol());
    if (protocol == null || asset.getHost() == null || asset.getPort() == null) {
      throw new ConnectionNotAllowedException("SSH/RDP로 접속 가능한 서버 자산이 아닙니다.");
    }

    String sessionId = UUID.randomUUID().toString();
    ConnectionSession session =
        sessionService.openSession(tenantId, userId, assetId, protocol, sessionId, clientIp);

    // 표준(상시) 권한이 없어도, 유효한 JIT 접근요청 승인이 있으면 기간 내 임시 접속을 허용한다.
    if (requireGrant
        && !accessibleAssetService.canAccess(tenantId, userId, assetId)
        && !approvalService.hasActiveApproval(tenantId, userId, assetId)) {
      sessionService.markFailed(tenantId, session.getId(), "PERMISSION_DENIED", "접근 권한 없음");
      throw new ConnectionNotAllowedException("해당 자산에 접근 권한이 없습니다.");
    }

    try {
      GuacamoleLaunch launch = guacamoleClient.createSession(new GuacamoleConnectionRequest(
          protocol, asset.getHost(), asset.getPort(), username, password, guacamoleLabel(asset)));
      sessionService.markConnected(tenantId, session.getId());
      return launch.redirectUrl();
    } catch (GuacamoleException exception) {
      sessionService.markFailed(tenantId, session.getId(), "GATEWAY_ERROR", "게이트웨이 오류");
      throw exception;
    }
  }

  public void close(UUID tenantId, UUID sessionId) {
    sessionService.markClosed(tenantId, sessionId);
  }

  /**
   * Guacamole 연결 식별자로 쓸 ASCII 라벨을 고른다. Guacamole 클라이언트는 URL의 base64 식별자를
   * Latin-1로 디코드하므로 비ASCII(한글 등) 이름은 라운드트립에서 깨진다. 이름이 ASCII면 그대로,
   * 아니면 host, 최후엔 자산 UUID를 쓴다(항상 ASCII 보장).
   */
  private static String guacamoleLabel(Asset asset) {
    for (String candidate : new String[] {asset.getName(), asset.getHost()}) {
      if (candidate != null && !candidate.isBlank()
          && candidate.chars().allMatch(c -> c >= 0x20 && c < 0x7f)) {
        return candidate;
      }
    }
    return asset.getId().toString();
  }

  private SessionProtocol toSessionProtocol(AssetProtocol protocol) {
    return switch (protocol) {
      case SSH -> SessionProtocol.SSH;
      case RDP -> SessionProtocol.RDP;
      default -> null;
    };
  }
}
