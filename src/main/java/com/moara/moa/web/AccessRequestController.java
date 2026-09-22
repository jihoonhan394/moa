package com.moara.moa.web;

import com.moara.moa.access.AccessApprovalService;
import com.moara.moa.access.AccessRequest;
import com.moara.moa.access.AccessRequestForm;
import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetProtocol;
import com.moara.moa.asset.AssetService;
import com.moara.moa.asset.AssetStatus;
import com.moara.moa.asset.AssetType;
import com.moara.moa.permission.AccessibleAssetService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 접근요청(JIT) — 요청자 화면. 표준 권한이 없는 서버를 골라 사유·기간과 함께 요청하고, 내 요청 현황을 본다.
 * 노출 최소화를 위해 요청 카탈로그에는 이름·분류·프로토콜만 보이고 host/port는 노출하지 않는다.
 */
@Controller
public class AccessRequestController {
  private final AccessApprovalService approvalService;
  private final AssetService assetService;
  private final AccessibleAssetService accessibleAssetService;
  private final ManagedUserService userService;
  private final TenantContext tenantContext;

  public AccessRequestController(
      AccessApprovalService approvalService, AssetService assetService,
      AccessibleAssetService accessibleAssetService, ManagedUserService userService,
      TenantContext tenantContext) {
    this.approvalService = approvalService;
    this.assetService = assetService;
    this.accessibleAssetService = accessibleAssetService;
    this.userService = userService;
    this.tenantContext = tenantContext;
  }

  public record RequestView(
      UUID id, String assetName, String reason, String period, String statusLabel,
      String approverName, String reviewComment, boolean canCancel) {}

  public record RequestableAsset(UUID id, String name, String category, String protocol) {}

  @GetMapping("/access-requests")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = tenantContext.currentUserId();
    List<AccessRequest> mine = approvalService.myRequests(tenantId, userId);

    Map<UUID, String> assetNames = new HashMap<>();
    List<RequestView> requests = mine.stream().map(r -> new RequestView(
        r.getId(), assetName(tenantId, r.getAssetId(), assetNames), r.getReason(),
        r.getRequestedStartAt().toLocalDateTime() + " ~ " + r.getRequestedEndAt().toLocalDateTime(),
        r.getStatus().getLabel(), userName(r.getApproverUserId()), r.getReviewComment(),
        r.getStatus() == com.moara.moa.access.AccessRequestStatus.PENDING)).toList();

    model.addAttribute("requests", requests);
    model.addAttribute("requestable", requestableAssets(tenantId, userId, mine));
    model.addAttribute("form", new AccessRequestForm(null, "", 4));
    model.addAttribute("page", "access-requests");
    model.addAttribute("pageTitle", "접근 요청");
    model.addAttribute("projectName", "MOA");
    return "access/requests";
  }

  @PostMapping("/access-requests")
  public String create(
      @Valid @ModelAttribute("form") AccessRequestForm form, BindingResult binding,
      RedirectAttributes redirect) {
    if (binding.hasErrors()) {
      redirect.addFlashAttribute("error",
          binding.getAllErrors().get(0).getDefaultMessage());
      return "redirect:/access-requests";
    }
    try {
      approvalService.request(tenantContext.currentTenantId(), tenantContext.currentUserId(), form);
      redirect.addFlashAttribute("message", "접근 요청을 보냈습니다. 승인 후 접속할 수 있습니다.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/access-requests";
  }

  @PostMapping("/access-requests/{id}/cancel")
  public String cancel(@PathVariable UUID id, RedirectAttributes redirect) {
    try {
      approvalService.cancel(tenantContext.currentTenantId(), id, tenantContext.currentUserId());
      redirect.addFlashAttribute("message", "요청을 취소했습니다.");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", exception.getMessage());
    }
    return "redirect:/access-requests";
  }

  /** 요청 가능한 서버: 활성 SSH/RDP 서버 중 내가 접근 불가하고 대기 요청이 없는 것(이름·분류만 노출). */
  private List<RequestableAsset> requestableAssets(UUID tenantId, UUID userId, List<AccessRequest> mine) {
    Set<UUID> accessible = accessibleAssetService.findAccessibleAssets(tenantId, userId).stream()
        .map(Asset::getId).collect(Collectors.toSet());
    Set<UUID> pending = mine.stream()
        .filter(r -> r.getStatus() == com.moara.moa.access.AccessRequestStatus.PENDING)
        .map(AccessRequest::getAssetId).collect(Collectors.toSet());
    return assetService.findAllByType(tenantId, AssetType.SERVER).stream()
        .filter(a -> a.getStatus() == AssetStatus.ACTIVE && isRemote(a.getProtocol()))
        .filter(a -> !accessible.contains(a.getId()) && !pending.contains(a.getId()))
        .map(a -> new RequestableAsset(
            a.getId(), a.getName(), a.getCategory() == null ? "" : a.getCategory(), a.getProtocol().name()))
        .toList();
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

  private String userName(UUID userId) {
    if (userId == null) {
      return null;
    }
    try {
      ManagedUser user = userService.findById(userId);
      return user.getName();
    } catch (RuntimeException notFound) {
      return null;
    }
  }

  private boolean isRemote(AssetProtocol protocol) {
    return protocol == AssetProtocol.SSH || protocol == AssetProtocol.RDP;
  }
}
