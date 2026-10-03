package com.moara.moa.inventory.tag;

import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.security.TenantContext;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 관리번호(QR 스티커) 발급 화면.
 *
 * <p>PC 전용이다. 스티커는 <b>인쇄해서 미리 만들어 두고</b> 현장에서 붙이는 것이라, 발급은 앉아서
 * 하는 일이고 붙이는 것만 현장 일이다.
 */
@Controller
public class AssetTagController {

  private static final String AUDIT_DOMAIN = "자산대장";

  private final AssetTagService tagService;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public AssetTagController(
      AssetTagService tagService, TenantAuditRecorder auditRecorder, TenantContext tenantContext) {
    this.tagService = tagService;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/inventory/tags")
  public String list(Model model) {
    List<AssetTag> unattached = tagService.unattached(tenantContext.currentTenantId());
    model.addAttribute("tags", unattached);
    model.addAttribute("page", "inventory-tags");
    model.addAttribute("pageTitle", "관리번호");
    model.addAttribute("projectName", "MOA");
    return "inventory/tags";
  }

  @PostMapping("/inventory/tags")
  public String issue(@RequestParam(defaultValue = "20") int count) {
    List<AssetTag> issued =
        tagService.issue(tenantContext.currentTenantId(), count, tenantContext.currentUserId());
    auditRecorder.record(AUDIT_DOMAIN, "ASSET_TAG_ISSUE", null, issued.size() + "장 발급");
    return "redirect:/inventory/tags";
  }
}
