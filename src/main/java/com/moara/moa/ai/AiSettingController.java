package com.moara.moa.ai;

import com.moara.moa.audit.AuditResult;
import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.security.TenantContext;
import jakarta.validation.Valid;
import java.util.Optional;
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
  private final AiService aiService;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public AiSettingController(
      AiSettingService settingService, AiService aiService,
      TenantAuditRecorder auditRecorder, TenantContext tenantContext) {
    this.settingService = settingService;
    this.aiService = aiService;
    this.auditRecorder = auditRecorder;
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

  /**
   * 저장된 설정으로 실제 한 번 호출해 본다.
   *
   * <p>저장만으로는 아무것도 증명되지 않는다 — 키가 틀려도 화면은 "저장했습니다"라고 하고,
   * 문제는 한참 뒤 다른 기능에서 조용한 비활성으로 나타난다. 여기서 바로 확인할 수 있어야
   * 설정 화면이 제 몫을 한다.
   *
   * <p>호출 자체가 비용이 드는 외부 요청이라 감사에 남긴다(성공·실패 모두).
   */
  @PostMapping("/ai-settings/test")
  public String test(RedirectAttributes redirect) {
    AiService.TestResult result = aiService.test(tenantContext.currentTenantId());
    redirect.addFlashAttribute("testResult", result);
    audit("AI_SETTING_TEST", result.ok() ? AuditResult.SUCCESS : AuditResult.FAILURE,
        "model=" + result.model() + " " + result.message());
    return "redirect:/ai-settings";
  }

  private void audit(String action, String message) {
    audit(action, AuditResult.SUCCESS, message);
  }

  private void audit(String action, AuditResult result, String message) {
    auditRecorder.record("AiSetting", action, null, result, message);
  }
}
