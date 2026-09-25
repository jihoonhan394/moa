package com.moara.moa.connection;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.audit.AuditLog;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 기관 관리자(TENANT_ADMIN)용 접속 이력. 자기 기관의 접속 세션(시도/성공/실패/종료)을 최신순으로 보여준다.
 * 접근 제어는 SecurityConfig(/access-history/**=TENANT_ADMIN)가 강제하며, 항상 현재 기관으로만 격리된다.
 * 사용자/자산 식별자는 기관 범위 목록으로 이름을 해석한다(삭제된 대상은 라벨 없이 식별자만).
 */
@Controller
public class ConnectionHistoryController {
  private static final int MAX_ROWS = 200;

  private final ConnectionSessionService sessionService;
  private final ManagedUserService userService;
  private final AssetService assetService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public ConnectionHistoryController(
      ConnectionSessionService sessionService,
      ManagedUserService userService,
      AssetService assetService,
      AuditLogService auditLogService,
      TenantContext tenantContext) {
    this.sessionService = sessionService;
    this.userService = userService;
    this.assetService = assetService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  /** 로그인 이력 한 줄(사용자·역할·시각·IP). */
  public record LoginRow(OffsetDateTime at, String user, String role, String ip) {}

  @GetMapping("/access-history")
  public String history(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    List<ConnectionSession> sessions =
        sessionService.findHistory(tenantId).stream().limit(MAX_ROWS).toList();

    Map<UUID, String> userNames = userService.labelsByTenant(tenantId);
    Map<UUID, String> assetNames = assetService.findAll(tenantId).stream()
        .collect(Collectors.toMap(Asset::getId, Asset::getName, (a, b) -> a));

    // 로그인 이력(USER_LOGIN 감사). 관리자를 알아볼 수 있게 역할 라벨을 붙인다.
    Map<UUID, ManagedUser> byId = userService.findByTenant(tenantId).stream()
        .collect(Collectors.toMap(ManagedUser::getId, u -> u, (a, b) -> a));
    List<LoginRow> logins = auditLogService.findRecentByAction(tenantId, "USER_LOGIN").stream()
        .map(a -> new LoginRow(
            a.getCreatedAt(),
            userNames.getOrDefault(a.getActorUserId(), "(알 수 없음)"),
            roleLabel(byId.get(a.getActorUserId())),
            a.getMessage() != null ? a.getMessage().replace("ip=", "") : "-"))
        .toList();

    model.addAttribute("logins", logins);
    model.addAttribute("sessions", sessions);
    model.addAttribute("userNames", userNames);
    model.addAttribute("assetNames", assetNames);
    model.addAttribute("total", sessions.size());
    model.addAttribute("capped", sessions.size() >= MAX_ROWS);
    return "access-history";
  }

  /** 대표 역할 라벨(관리자 식별용). 관리 역할 우선, 없으면 일반 사용자. */
  private String roleLabel(ManagedUser user) {
    if (user == null) {
      return "-";
    }
    if (user.hasRole(com.moara.moa.user.UserRole.TENANT_ADMIN)) {
      return "기관 관리자";
    }
    if (user.hasRole(com.moara.moa.user.UserRole.INFRA_MANAGER)) {
      return "인프라 관리자";
    }
    if (user.hasRole(com.moara.moa.user.UserRole.ASSET_MANAGER)) {
      return "자산 관리자";
    }
    return "일반 사용자";
  }
}
