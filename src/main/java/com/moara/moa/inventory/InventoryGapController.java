package com.moara.moa.inventory;

import com.moara.moa.security.TenantContext;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 자산 대장 점검 화면. 경로가 {@code /inventory/**} 아래라 자산 관리자 인가를 그대로 물려받는다.
 */
@Controller
public class InventoryGapController {
  private final InventoryGapService gapService;
  private final TenantContext tenantContext;

  public InventoryGapController(InventoryGapService gapService, TenantContext tenantContext) {
    this.gapService = gapService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/inventory/gaps")
  public String gaps(Model model) {
    model.addAttribute("gaps", gapService.gaps(tenantContext.currentTenantId(), LocalDate.now()));
    model.addAttribute("staleDays", InventoryGapService.STALE_DAYS);
    model.addAttribute("page", "inventory");
    return "inventory/gaps";
  }
}
