package com.moara.moa.web;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.tracker.ResourceTrackerService;
import com.moara.moa.tracker.SslProbeService;
import com.moara.moa.tracker.TrackerTargetType;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 자원 만기·점검 항목 관리 UI. 인벤토리(자산 관리자)·서버/접속 자산(인프라 관리자) 양쪽에 붙는다.
 * 라우트 접두사(/inventory, /assets)로 기존 역할 인가를 그대로 재사용한다. 자산에는 SSL 자동감지가 추가된다.
 */
@Controller
public class TrackerController {
  private final ResourceTrackerService trackerService;
  private final InventoryItemService inventoryService;
  private final AssetService assetService;
  private final SslProbeService sslProbeService;
  private final TenantContext tenantContext;

  public TrackerController(
      ResourceTrackerService trackerService, InventoryItemService inventoryService,
      AssetService assetService, SslProbeService sslProbeService, TenantContext tenantContext) {
    this.trackerService = trackerService;
    this.inventoryService = inventoryService;
    this.assetService = assetService;
    this.sslProbeService = sslProbeService;
    this.tenantContext = tenantContext;
  }

  // ── 인벤토리 대상 ────────────────────────────────────────────────
  @GetMapping("/inventory/{id}/trackers")
  public String inventoryTrackers(@PathVariable UUID id, Model model) {
    InventoryItem item = inventoryService.findById(tenantContext.currentTenantId(), id);
    populate(model, TrackerTargetType.INVENTORY, id, item.getName(), "/inventory/" + id, "/inventory", false);
    return "trackers/panel";
  }

  @PostMapping("/inventory/{id}/trackers")
  public String addInventoryTracker(
      @PathVariable UUID id, @RequestParam String label,
      @RequestParam LocalDate dueOn, @RequestParam(required = false) Integer recurEveryDays) {
    UUID tenantId = tenantContext.currentTenantId();
    inventoryService.findById(tenantId, id); // 소유권 검증 — 타 기관 항목에 트래커 생성 차단
    trackerService.add(tenantId, TrackerTargetType.INVENTORY, id, label, dueOn, recurEveryDays);
    return "redirect:/inventory/" + id + "/trackers";
  }

  @PostMapping("/inventory/{id}/trackers/{trackerId}/done")
  public String doneInventoryTracker(@PathVariable UUID id, @PathVariable UUID trackerId) {
    trackerService.markDone(tenantContext.currentTenantId(), trackerId);
    return "redirect:/inventory/" + id + "/trackers";
  }

  @PostMapping("/inventory/{id}/trackers/{trackerId}/delete")
  public String deleteInventoryTracker(@PathVariable UUID id, @PathVariable UUID trackerId) {
    trackerService.remove(tenantContext.currentTenantId(), trackerId);
    return "redirect:/inventory/" + id + "/trackers";
  }

  // ── 서버/접속 자산 대상 ──────────────────────────────────────────
  @GetMapping("/assets/{id}/trackers")
  public String assetTrackers(@PathVariable UUID id, Model model) {
    Asset asset = assetService.findById(tenantContext.currentTenantId(), id);
    populate(model, TrackerTargetType.ASSET, id, asset.getName(), "/assets/" + id, "/assets", true);
    model.addAttribute("sslHost", probeHost(asset));
    model.addAttribute("sslPort", 443);
    return "trackers/panel";
  }

  @PostMapping("/assets/{id}/trackers")
  public String addAssetTracker(
      @PathVariable UUID id, @RequestParam String label,
      @RequestParam LocalDate dueOn, @RequestParam(required = false) Integer recurEveryDays) {
    UUID tenantId = tenantContext.currentTenantId();
    assetService.findById(tenantId, id); // 소유권 검증 — 타 기관 자산에 트래커 생성 차단
    trackerService.add(tenantId, TrackerTargetType.ASSET, id, label, dueOn, recurEveryDays);
    return "redirect:/assets/" + id + "/trackers";
  }

  @PostMapping("/assets/{id}/trackers/{trackerId}/done")
  public String doneAssetTracker(@PathVariable UUID id, @PathVariable UUID trackerId) {
    trackerService.markDone(tenantContext.currentTenantId(), trackerId);
    return "redirect:/assets/" + id + "/trackers";
  }

  @PostMapping("/assets/{id}/trackers/{trackerId}/delete")
  public String deleteAssetTracker(@PathVariable UUID id, @PathVariable UUID trackerId) {
    trackerService.remove(tenantContext.currentTenantId(), trackerId);
    return "redirect:/assets/" + id + "/trackers";
  }

  /** SSL 자동감지: 대상 호스트에 TLS 핸드셰이크로 인증서 만료일을 읽어 트래커로 upsert. */
  @PostMapping("/assets/{id}/trackers/probe-ssl")
  public String probeSsl(
      @PathVariable UUID id, @RequestParam String host, @RequestParam int port,
      RedirectAttributes redirect) {
    assetService.findById(tenantContext.currentTenantId(), id); // 소유권 검증
    sslProbeService.probe(host, port).ifPresentOrElse(
        result -> {
          String detail = SslProbeService.summarize(result);
          trackerService.upsertProbed(
              tenantContext.currentTenantId(), TrackerTargetType.ASSET, id,
              "SSL 인증서 (" + host + ")", result.notAfter(), ResourceTrackerService.SOURCE_SSL,
              host, port, detail);
          redirect.addFlashAttribute("probeMessage",
              "SSL 인증서 만료일 " + result.notAfter() + " 감지됨 — " + detail
                  + " (이후 매일 자동 갱신).");
        },
        () -> redirect.addFlashAttribute("probeError",
            host + ":" + port + " 에서 인증서를 읽지 못했습니다(연결 불가/TLS 아님)."));
    return "redirect:/assets/" + id + "/trackers";
  }

  private String probeHost(Asset asset) {
    if (asset.getHost() != null && !asset.getHost().isBlank()) {
      return asset.getHost();
    }
    String url = asset.getUrl();
    if (url == null || url.isBlank()) {
      return "";
    }
    try {
      String h = java.net.URI.create(url).getHost();
      return h != null ? h : url;
    } catch (RuntimeException exception) {
      return url;
    }
  }

  private void populate(
      Model model, TrackerTargetType targetType, UUID targetId, String targetName,
      String basePath, String backPath, boolean asset) {
    model.addAttribute("targetName", targetName);
    model.addAttribute("basePath", basePath);
    model.addAttribute("backPath", backPath);
    model.addAttribute("isAsset", asset);
    model.addAttribute("trackers",
        trackerService.forTarget(tenantContext.currentTenantId(), targetType, targetId));
    model.addAttribute("today", LocalDate.now());
    model.addAttribute("page", asset ? "assets" : "inventory");
    model.addAttribute("pageTitle", "만기·점검");
    model.addAttribute("projectName", "MOA");
  }
}
