package com.moara.moa.ai;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 기관별 AI 설정(기관 관리자). 제공자·모델·API 키(볼트 암호화) 관리. */
@Controller
public class AiSettingController {
  private final AiSettingService settingService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public AiSettingController(
      AiSettingService settingService, AuditLogService auditLogService, TenantContext tenantContext) {
    this.settingService = settingService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/ai-settings")
  public String view(Model model) {
    Optional<AiSetting> setting = settingService.findForTenant(tenantContext.currentTenantId());
    if (!model.containsAttribute("aiForm")) {
      model.addAttribute("aiForm", AiSettingForm.from(setting.orElse(null)));
    }
    model.addAttribute("providers", AiProvider.values());
    model.addAttribute("hasSecret", setting.map(AiSetting::hasSecret).orElse(false));
    model.addAttribute("enabled", setting.map(AiSetting::isEnabled).orElse(false));
    model.addAttribute("page", "ai-settings");
    return "ai-settings";
  }

  @PostMapping("/ai-settings")
  public String save(
      @Valid @ModelAttribute("aiForm") AiSettingForm aiForm, BindingResult binding, Model model,
      RedirectAttributes redirect) {
    if (binding.hasErrors()) {
      model.addAttribute("providers", AiProvider.values());
      model.addAttribute("page", "ai-settings");
      return "ai-settings";
    }
    settingService.saveForTenant(tenantContext.currentTenantId(), aiForm);
    audit("AI_SETTING_SAVE", aiForm.provider() + " enabled=" + aiForm.enabled());
    redirect.addFlashAttribute("aiMessage", "AI 설정을 저장했습니다.");
    return "redirect:/ai-settings";
  }

  private void audit(String action, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "AiSetting", null, AuditResult.SUCCESS, message);
    }
  }
}
