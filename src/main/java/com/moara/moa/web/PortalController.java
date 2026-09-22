package com.moara.moa.web;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetType;
import com.moara.moa.audit.AuditLog;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.connection.ConnectionSession;
import com.moara.moa.connection.ConnectionSessionService;
import com.moara.moa.permission.AccessibleAssetService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 로그인 사용자가 접근 가능한 자산 포털과 웹 자산 링크 런처.
 * 테넌트는 {@link TenantContext}, 사용자 식별자는 principal({@link MoaUserDetails})에서 얻는다.
 * 웹 자산 열람은 접근 권한 검사 후 감사 로그로 기록하고 저장된 URL로 리다이렉트한다.
 */
@Controller
public class PortalController {
  private static final String WEB_OPEN_ACTION = "WEB_ASSET_OPEN";

  private final AccessibleAssetService accessibleAssetService;
  private final AssetService assetService;
  private final AuditLogService auditLogService;
  private final ConnectionSessionService connectionSessionService;
  private final TenantContext tenantContext;

  public PortalController(
      AccessibleAssetService accessibleAssetService,
      AssetService assetService,
      AuditLogService auditLogService,
      ConnectionSessionService connectionSessionService,
      TenantContext tenantContext) {
    this.accessibleAssetService = accessibleAssetService;
    this.assetService = assetService;
    this.auditLogService = auditLogService;
    this.connectionSessionService = connectionSessionService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/portal")
  public String portal(
      Model model,
      @AuthenticationPrincipal MoaUserDetails principal,
      @RequestParam(required = false) String error) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = principal.getUserId();
    model.addAttribute("assets", accessibleAssetService.findAccessibleAssets(tenantId, userId));
    model.addAttribute("webHistory", recentWebOpens(userId));
    model.addAttribute("sessions", recentSessions(tenantId, userId));
    model.addAttribute("error", error);
    model.addAttribute("page", "portal");
    model.addAttribute("pageTitle", "내 접속 가능 자산");
    model.addAttribute("projectName", "MOA");
    return "portal";
  }

  /**
   * 웹 자산 링크를 연다. 접근 권한을 검사하고 감사 로그로 열람 이력을 남긴 뒤 저장된 URL로 리다이렉트한다.
   * 권한이 없거나 웹 자산이 아니거나 URL이 http(s)가 아니면 열지 않는다(오픈 리다이렉트 방어).
   */
  @GetMapping("/portal/assets/{id}/open")
  public String openWebAsset(@PathVariable UUID id, @AuthenticationPrincipal MoaUserDetails principal) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = principal.getUserId();

    if (!accessibleAssetService.canAccess(tenantId, userId, id)) {
      recordOpen(tenantId, userId, id, AuditResult.FAILURE, "access denied");
      return "redirect:/portal?error=denied";
    }
    Asset asset = assetService.findById(tenantId, id);
    String url = asset.getUrl();
    if (asset.getAssetType() != AssetType.WEBSITE || !isHttpUrl(url)) {
      recordOpen(tenantId, userId, id, AuditResult.FAILURE, "not an openable web asset");
      return "redirect:/portal?error=invalid";
    }
    recordOpen(tenantId, userId, id, AuditResult.SUCCESS, asset.getName());
    return "redirect:" + url;
  }

  private List<AuditLog> recentWebOpens(UUID userId) {
    return auditLogService.findByActor(userId).stream()
        .filter(log -> WEB_OPEN_ACTION.equals(log.getAction()))
        .limit(10)
        .toList();
  }

  private List<ConnectionSession> recentSessions(UUID tenantId, UUID userId) {
    return connectionSessionService.findHistoryForUser(tenantId, userId).stream()
        .limit(10)
        .toList();
  }

  private void recordOpen(UUID tenantId, UUID userId, UUID assetId, AuditResult result, String message) {
    auditLogService.recordTenantAction(tenantId, userId, WEB_OPEN_ACTION, "Asset", assetId, result, message);
  }

  private boolean isHttpUrl(String url) {
    if (url == null) {
      return false;
    }
    String lower = url.toLowerCase();
    return lower.startsWith("http://") || lower.startsWith("https://");
  }
}
