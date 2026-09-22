package com.moara.moa.web;

import com.moara.moa.access.AccessApprovalService;
import com.moara.moa.access.AccessRequest;
import com.moara.moa.credential.Credential;
import com.moara.moa.credential.CredentialService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 접근요청(JIT) — 승인자 화면. 소유팀 리더·관리자가 대기 요청을 승인/반려하고, 유효 승인을 회수한다.
 * 승인 시 선택적으로 볼트 자격증명을 함께 내줄 수 있다. 실제 승인 권한은 서비스에서 재검증한다(default-deny).
 */
@Controller
public class AccessApprovalController {
  private final AccessApprovalService approvalService;
  private final CredentialService credentialService;
  private final ManagedUserService userService;
  private final com.moara.moa.asset.AssetService assetService;
  private final TenantContext tenantContext;

  public AccessApprovalController(
      AccessApprovalService approvalService, CredentialService credentialService,
      ManagedUserService userService, com.moara.moa.asset.AssetService assetService,
      TenantContext tenantContext) {
    this.approvalService = approvalService;
    this.credentialService = credentialService;
    this.userService = userService;
    this.assetService = assetService;
    this.tenantContext = tenantContext;
  }

  public record PendingView(
      UUID id, String requesterName, String assetName, String reason, String period) {}

  public record GrantView(UUID id, String requesterName, String assetName, String until) {}

  public record CredentialOption(UUID id, String label) {}

  @GetMapping("/access-approvals")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID approverId = tenantContext.currentUserId();
    Map<UUID, String> assetNames = new HashMap<>();
    Map<UUID, String> userNames = new HashMap<>();

    List<PendingView> pending = approvalService.pendingForApprover(tenantId, approverId).stream()
        .map(r -> new PendingView(
            r.getId(), userName(r.getRequesterUserId(), userNames), assetName(tenantId, r.getAssetId(), assetNames),
            r.getReason(),
            r.getRequestedStartAt().toLocalDateTime() + " ~ " + r.getRequestedEndAt().toLocalDateTime()))
        .toList();

    List<GrantView> grants = approvalService.activeGrantsForApprover(tenantId, approverId).stream()
        .map(r -> new GrantView(
            r.getId(), userName(r.getRequesterUserId(), userNames), assetName(tenantId, r.getAssetId(), assetNames),
            r.getRequestedEndAt().toLocalDateTime().toString()))
        .toList();

    List<CredentialOption> credentials = credentialService.findAll(tenantId).stream()
        .map(c -> new CredentialOption(c.getId(), credentialLabel(c))).toList();

    model.addAttribute("pending", pending);
    model.addAttribute("grants", grants);
    model.addAttribute("credentials", credentials);
    model.addAttribute("page", "access-approvals");
    model.addAttribute("pageTitle", "접근 승인");
    model.addAttribute("projectName", "MOA");
    return "access/approvals";
  }

  @PostMapping("/access-approvals/{id}/approve")
  public String approve(
      @PathVariable UUID id,
      @RequestParam(required = false) String credentialId,
      @RequestParam(required = false) String comment,
      RedirectAttributes redirect) {
    try {
      approvalService.approve(
          tenantContext.currentTenantId(), id, tenantContext.currentUserId(), parseUuid(credentialId), comment);
      redirect.addFlashAttribute("message", "요청을 승인했습니다.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/access-approvals";
  }

  @PostMapping("/access-approvals/{id}/reject")
  public String reject(
      @PathVariable UUID id, @RequestParam(required = false) String comment, RedirectAttributes redirect) {
    try {
      approvalService.reject(tenantContext.currentTenantId(), id, tenantContext.currentUserId(), comment);
      redirect.addFlashAttribute("message", "요청을 반려했습니다.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/access-approvals";
  }

  @PostMapping("/access-approvals/{id}/revoke")
  public String revoke(
      @PathVariable UUID id, @RequestParam(required = false) String comment, RedirectAttributes redirect) {
    try {
      approvalService.revoke(tenantContext.currentTenantId(), id, tenantContext.currentUserId(), comment);
      redirect.addFlashAttribute("message", "임시 접근 권한을 회수했습니다.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/access-approvals";
  }

  private static UUID parseUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private String credentialLabel(Credential credential) {
    String username = credential.getUsername();
    return credential.getName() + (username == null || username.isBlank() ? "" : " (" + username + ")");
  }

  private String assetName(UUID tenantId, UUID assetId, Map<UUID, String> cache) {
    return cache.computeIfAbsent(assetId, id -> {
      try {
        return assetService.findById(tenantId, id).getName();
      } catch (RuntimeException notFound) {
        return "(삭제된 자산)";
      }
    });
  }

  private String userName(UUID userId, Map<UUID, String> cache) {
    if (userId == null) {
      return "-";
    }
    return cache.computeIfAbsent(userId, id -> {
      try {
        ManagedUser user = userService.findById(id);
        return user.getName();
      } catch (RuntimeException notFound) {
        return "(알 수 없음)";
      }
    });
  }
}
