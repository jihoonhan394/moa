package com.moara.moa.connection;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetType;
import com.moara.moa.guacamole.GuacamoleException;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 인프라 관리자가 관리하는 서버에 바로 웹 SSH/RDP 접속. 라우트(/servers/**)가 INFRA_MANAGER로 게이트되므로
 * 사용자별 접근권한 부여 없이 자기 기관 서버에 접속한다(관리 대상 = 접속 가능, 논의된 역할 모델).
 * 접속 폼/템플릿은 소비자 포털과 공유(portal/connect)하되 폼 action·돌아가기 링크만 다르게 준다.
 */
@Controller
public class ServerConnectionController {
  private final ConnectionLaunchService launchService;
  private final AssetService assetService;
  private final TenantContext tenantContext;

  public ServerConnectionController(
      ConnectionLaunchService launchService, AssetService assetService, TenantContext tenantContext) {
    this.launchService = launchService;
    this.assetService = assetService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/servers/{id}/connect")
  public String connectForm(
      @PathVariable UUID id,
      @RequestParam(required = false) String error,
      Model model) {
    Asset asset = assetService.findById(tenantContext.currentTenantId(), id); // 타 기관 NotFound
    if (asset.getAssetType() != AssetType.SERVER || !isRemoteProtocol(asset.getProtocol())) {
      return "redirect:/servers";
    }
    model.addAttribute("asset", asset);
    model.addAttribute("guacEnabled", launchService.isEnabled());
    model.addAttribute("error", error);
    model.addAttribute("connectAction", "/servers/" + id + "/connect");
    model.addAttribute("backLink", "/servers");
    model.addAttribute("backLabel", "서버");
    model.addAttribute("page", "servers");
    model.addAttribute("pageTitle", "서버 접속");
    model.addAttribute("projectName", "MOA");
    return "portal/connect";
  }

  @PostMapping("/servers/{id}/connect")
  public String connect(
      @PathVariable UUID id,
      @RequestParam String username,
      @RequestParam String password,
      @AuthenticationPrincipal MoaUserDetails principal,
      HttpServletRequest request) {
    if (!launchService.isEnabled()) {
      return "redirect:/servers/" + id + "/connect?error=gateway";
    }
    try {
      String redirectUrl = launchService.launchAsManager(
          tenantContext.currentTenantId(), principal.getUserId(), id, username, password,
          request.getRemoteAddr());
      return "redirect:" + redirectUrl;
    } catch (ConnectionNotAllowedException exception) {
      return "redirect:/servers/" + id + "/connect?error=gateway";
    } catch (GuacamoleException exception) {
      return "redirect:/servers/" + id + "/connect?error=gateway";
    }
  }

  private boolean isRemoteProtocol(AssetProtocol protocol) {
    return protocol == AssetProtocol.SSH || protocol == AssetProtocol.RDP;
  }
}
