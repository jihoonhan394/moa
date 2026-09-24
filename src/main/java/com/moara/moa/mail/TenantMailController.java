package com.moara.moa.mail;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 기관 메일 콘솔. 기관 관리자(TENANT_ADMIN)가 자기 기관 사용자에게 발송하기 위한 SMTP 설정과 발송.
 * /mail/** 는 SecurityConfig에서 SYSTEM_ADMIN/TENANT_ADMIN 전용이며, 여기서는 항상 자기 기관 스코프만 다룬다.
 */
@Controller
public class TenantMailController {
  private static final Logger log = LoggerFactory.getLogger(TenantMailController.class);
  private final MailSettingService settingService;
  private final MailService mailService;
  private final ManagedUserService userService;
  private final TenantContext tenantContext;
  private final AuditLogService auditLogService;

  public TenantMailController(
      MailSettingService settingService,
      MailService mailService,
      ManagedUserService userService,
      TenantContext tenantContext,
      AuditLogService auditLogService) {
    this.settingService = settingService;
    this.mailService = mailService;
    this.userService = userService;
    this.tenantContext = tenantContext;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/mail")
  public String mail(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    Optional<MailSetting> setting = settingService.findForTenant(tenantId);
    if (!model.containsAttribute("mailForm")) {
      model.addAttribute("mailForm", MailSettingForm.from(setting.orElse(null)));
    }
    model.addAttribute("configured", setting.isPresent());
    model.addAttribute("hasSecret", setting.map(MailSetting::hasSecret).orElse(false));
    model.addAttribute("recipientCount", userService.activeUserEmails(tenantId).size());
    return "mail";
  }

  @PostMapping("/mail")
  public String save(
      @Valid @ModelAttribute("mailForm") MailSettingForm mailForm,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    if (bindingResult.hasErrors()) {
      Optional<MailSetting> setting = settingService.findForTenant(tenantId);
      model.addAttribute("configured", setting.isPresent());
      model.addAttribute("hasSecret", setting.map(MailSetting::hasSecret).orElse(false));
      model.addAttribute("recipientCount", userService.activeUserEmails(tenantId).size());
      return "mail";
    }
    settingService.saveForTenant(tenantId, mailForm);
    audit("MAIL_SETTING_SAVE", AuditResult.SUCCESS,
        "SMTP 설정 저장(호스트 " + mailForm.host() + ":" + mailForm.port() + ")");
    redirect.addFlashAttribute("message", "SMTP 설정을 저장했습니다.");
    return "redirect:/mail";
  }

  @PostMapping("/mail/test")
  public String test(@RequestParam String testTo, RedirectAttributes redirect) {
    MailSetting setting = settingService.findForTenant(tenantContext.currentTenantId()).orElse(null);
    if (setting == null) {
      redirect.addFlashAttribute("error", "먼저 SMTP 설정을 저장하세요.");
      return "redirect:/mail";
    }
    try {
      mailService.sendTest(setting, testTo);
      audit("MAIL_TEST_SEND", AuditResult.SUCCESS, "테스트 발송 성공(호스트 " + setting.getHost() + ")");
      redirect.addFlashAttribute("message", "테스트 메일을 발송했습니다: " + testTo);
    } catch (MailNotAllowedException notAllowed) {
      audit("MAIL_TEST_SEND", AuditResult.FAILURE, "테스트 발송 거부(호스트 " + setting.getHost() + ")");
      redirect.addFlashAttribute("error", "테스트 발송 실패: " + notAllowed.getMessage());
    } catch (RuntimeException exception) {
      log.warn("기관 SMTP 테스트 발송 실패 (tenant={})", tenantContext.currentTenantId(), exception);
      audit("MAIL_TEST_SEND", AuditResult.FAILURE, "테스트 발송 실패(호스트 " + setting.getHost() + ")");
      redirect.addFlashAttribute("error", "테스트 발송에 실패했습니다. SMTP 설정(호스트/포트/인증)을 확인하세요.");
    }
    return "redirect:/mail";
  }

  @PostMapping("/mail/send")
  public String send(
      @RequestParam String subject,
      @RequestParam String body,
      @RequestParam(defaultValue = "ALL") String recipientMode,
      @RequestParam(required = false) String manualRecipients,
      RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    MailSetting setting = settingService.findForTenant(tenantId).orElse(null);
    if (setting == null || !setting.isSendable()) {
      redirect.addFlashAttribute("error", "SMTP 설정이 없거나 비활성 상태입니다.");
      return "redirect:/mail";
    }
    List<String> to = "MANUAL".equals(recipientMode)
        ? parseManual(manualRecipients) : userService.activeUserEmails(tenantId);
    if (to.isEmpty()) {
      redirect.addFlashAttribute("error", "수신자가 없습니다.");
      return "redirect:/mail";
    }
    try {
      MailSendResult result = mailService.sendBulk(setting, to, subject, body);
      audit("MAIL_BROADCAST_SEND", AuditResult.SUCCESS,
          "전체 발송 " + to.size() + "명(성공 " + result.sent() + ", 실패 " + result.failed() + ")");
      redirect.addFlashAttribute("message",
          "발송 완료 — 성공 " + result.sent() + "통, 실패 " + result.failed() + "통");
    } catch (MailNotAllowedException notAllowed) {
      audit("MAIL_BROADCAST_SEND", AuditResult.FAILURE, "전체 발송 " + to.size() + "명 대상 거부");
      redirect.addFlashAttribute("error", "발송 실패: " + notAllowed.getMessage());
    } catch (RuntimeException exception) {
      log.warn("기관 SMTP 발송 실패 (tenant={})", tenantId, exception);
      audit("MAIL_BROADCAST_SEND", AuditResult.FAILURE, "전체 발송 " + to.size() + "명 대상 실패");
      redirect.addFlashAttribute("error", "발송에 실패했습니다. SMTP 설정을 확인하세요.");
    }
    return "redirect:/mail";
  }

  private List<String> parseManual(String raw) {
    List<String> out = new ArrayList<>();
    if (raw == null) {
      return out;
    }
    for (String token : raw.split("[,;\\s]+")) {
      if (!token.isBlank()) {
        out.add(token.trim());
      }
    }
    return out;
  }

  private void audit(String action, AuditResult result, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "MailSetting", null, result, message);
    }
  }
}
