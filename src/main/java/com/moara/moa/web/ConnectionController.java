package com.moara.moa.web;

import com.moara.moa.access.AccessApprovalService;
import com.moara.moa.access.AccessRequest;
import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetType;
import com.moara.moa.connection.ConnectionLaunchService;
import com.moara.moa.connection.ConnectionNotAllowedException;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.guacamole.GuacamoleException;
import com.moara.moa.permission.AccessibleAssetService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 서버 자산 웹 SSH/RDP 접속(Guacamole). 접속 시점 입력한 자격증명은 저장하지 않고 1회성 세션 생성에만 쓴다.
 */
@Controller
public class ConnectionController {
  private final ConnectionLaunchService launchService;
  private final AssetService assetService;
  private final AccessibleAssetService accessibleAssetService;
  private final AccessApprovalService approvalService;
  private final CredentialService credentialService;
  private final TenantContext tenantContext;

  public ConnectionController(
      ConnectionLaunchService launchService,
      AssetService assetService,
      AccessibleAssetService accessibleAssetService,
      AccessApprovalService approvalService,
      CredentialService credentialService,
      TenantContext tenantContext) {
    this.launchService = launchService;
    this.assetService = assetService;
    this.accessibleAssetService = accessibleAssetService;
    this.approvalService = approvalService;
    this.credentialService = credentialService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/portal/assets/{id}/connect")
  public String connectForm(
      @PathVariable UUID id,
      @AuthenticationPrincipal MoaUserDetails principal,
      @RequestParam(required = false) String error,
      Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = principal.getUserId();
    boolean standingAccess = accessibleAssetService.canAccess(tenantId, userId, id);
    // 표준 권한이 없어도 유효한 JIT 승인이 있으면 접속 화면을 연다.
    if (!standingAccess && !approvalService.hasActiveApproval(tenantId, userId, id)) {
      return "redirect:/portal?error=denied";
    }
    Asset asset = assetService.findById(tenantId, id);
    if (asset.getAssetType() != AssetType.SERVER || !isRemoteProtocol(asset.getProtocol())) {
      return "redirect:/portal?error=invalid";
    }
    // 승인에 볼트 자격증명이 함께 부여됐으면, 비밀번호 입력 없이 서버측 주입으로 접속하는 버튼을 노출한다.
    Optional<AccessRequest> approval = approvalService.activeApproval(tenantId, userId, id);
    if (approval.isPresent() && approval.get().getCredentialId() != null) {
      model.addAttribute("grantedConnectAction", "/portal/assets/" + id + "/connect-granted");
    }
    model.addAttribute("asset", asset);
    model.addAttribute("guacEnabled", launchService.isEnabled());
    model.addAttribute("error", error);
    model.addAttribute("connectAction", "/portal/assets/" + id + "/connect");
    model.addAttribute("backLink", "/portal");
    model.addAttribute("backLabel", "포털");
    model.addAttribute("page", "portal");
    model.addAttribute("pageTitle", "서버 접속");
    model.addAttribute("projectName", "MOA");
    return "portal/connect";
  }

  @PostMapping("/portal/assets/{id}/connect")
  public String connect(
      @PathVariable UUID id,
      @RequestParam String username,
      @RequestParam String password,
      @AuthenticationPrincipal MoaUserDetails principal,
      HttpServletRequest request) {
    if (!launchService.isEnabled()) {
      return "redirect:/portal/assets/" + id + "/connect?error=gateway";
    }
    try {
      String redirectUrl = launchService.launch(
          tenantContext.currentTenantId(), principal.getUserId(), id, username, password, request.getRemoteAddr());
      return "redirect:" + redirectUrl;
    } catch (ConnectionNotAllowedException exception) {
      return "redirect:/portal?error=denied";
    } catch (GuacamoleException exception) {
      return "redirect:/portal/assets/" + id + "/connect?error=gateway";
    }
  }

  /**
   * 승인 자격증명 주입 접속: 유효 승인에 부여된 볼트 자격증명을 서버측에서 복호화해 Guacamole 토큰에만 싣는다.
   * 사용자는 비밀번호를 보지 못하며, 평문은 저장되지 않는다. 접근 게이트는 launch가 승인으로 재검증한다.
   */
  @PostMapping("/portal/assets/{id}/connect-granted")
  public String connectGranted(
      @PathVariable UUID id,
      @AuthenticationPrincipal MoaUserDetails principal,
      HttpServletRequest request) {
    if (!launchService.isEnabled()) {
      return "redirect:/portal/assets/" + id + "/connect?error=gateway";
    }
    UUID tenantId = tenantContext.currentTenantId();
    Optional<AccessRequest> approval = approvalService.activeApproval(tenantId, principal.getUserId(), id);
    if (approval.isEmpty() || approval.get().getCredentialId() == null) {
      return "redirect:/portal?error=denied";
    }
    try {
      CredentialService.ResolvedCredential resolved =
          credentialService.resolveSecret(tenantId, approval.get().getCredentialId());
      String redirectUrl = launchService.launch(
          tenantId, principal.getUserId(), id, resolved.username(), resolved.secret(), request.getRemoteAddr());
      return "redirect:" + redirectUrl;
    } catch (ConnectionNotAllowedException exception) {
      return "redirect:/portal?error=denied";
    } catch (GuacamoleException exception) {
      return "redirect:/portal/assets/" + id + "/connect?error=gateway";
    }
  }

  @PostMapping("/portal/sessions/{id}/close")
  public String close(@PathVariable UUID id) {
    launchService.close(tenantContext.currentTenantId(), id);
    return "redirect:/portal";
  }

  private boolean isRemoteProtocol(AssetProtocol protocol) {
    return protocol == AssetProtocol.SSH || protocol == AssetProtocol.RDP;
  }
}
