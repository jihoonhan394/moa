package com.moara.moa.web;

import com.moara.moa.expiration.ExpirationImpactService;
import com.moara.moa.expiration.ExpirationRow;
import com.moara.moa.expiration.ExpirationService;
import com.moara.moa.expiration.ExpirationSourceType;
import com.moara.moa.security.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 만료/갱신 통합 대시보드(관리자). 인벤토리 라이선스·기간제 접근 권한·기관 구독의 만료를 임박순으로 모아 본다.
 * 각 항목에 대해 AI가 영향분석·후속조치 초안을 생성한다(누가/무엇이 영향받는지는 MOA만 계산 가능).
 */
@Controller
public class ExpirationController {
  private final ExpirationService expirationService;
  private final ExpirationImpactService impactService;
  private final TenantContext tenantContext;

  public ExpirationController(
      ExpirationService expirationService, ExpirationImpactService impactService,
      TenantContext tenantContext) {
    this.expirationService = expirationService;
    this.impactService = impactService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/expirations")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    List<ExpirationRow> rows = expirationService.findAll(tenantId);
    model.addAttribute("rows", rows);
    model.addAttribute("overdue", rows.stream().filter(r -> r.daysLeft() < 0).count());
    model.addAttribute("soon", rows.stream().filter(r -> r.daysLeft() >= 0 && r.daysLeft() <= 14).count());
    model.addAttribute("aiConfigured", impactService.aiConfigured(tenantId));
    model.addAttribute("page", "expirations");
    model.addAttribute("pageTitle", "만료 관리");
    model.addAttribute("projectName", "MOA");
    return "expirations/list";
  }

  @PostMapping("/expirations/ai-draft")
  public String aiDraft(
      @RequestParam ExpirationSourceType sourceType,
      @RequestParam(required = false) UUID sourceId,
      @RequestParam String category,
      @RequestParam String label,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn,
      @RequestParam long daysLeft,
      Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("label", label);
    model.addAttribute("category", category);
    model.addAttribute("expiresOn", expiresOn);
    model.addAttribute("daysLeft", daysLeft);
    model.addAttribute("page", "expirations");
    if (!impactService.aiConfigured(tenantId)) {
      model.addAttribute("error", "AI가 설정되지 않았습니다. 'AI 설정'에서 제공자와 API 키를 등록하세요.");
      return "expirations/ai-draft";
    }
    try {
      model.addAttribute("draft",
          impactService.draft(tenantId, sourceType, sourceId, category, label, expiresOn, daysLeft));
    } catch (RuntimeException exception) {
      model.addAttribute("error", "AI 초안 생성에 실패했습니다: " + exception.getMessage());
    }
    return "expirations/ai-draft";
  }
}
