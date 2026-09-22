package com.moara.moa.web;

import com.moara.moa.mail.MailNotAllowedException;
import com.moara.moa.mail.MailSendResult;
import com.moara.moa.mail.MailService;
import com.moara.moa.mail.MailSetting;
import com.moara.moa.mail.MailSettingForm;
import com.moara.moa.mail.MailSettingService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * 플랫폼(MOA) 메일 콘솔. MOA가 각 기관 관리자(TENANT_ADMIN)에게 발송하기 위한 SMTP 설정과
 * 발송을 담당한다. /admin/** 는 SecurityConfig에서 SYSTEM_ADMIN 전용이다.
 */
@Controller
public class PlatformMailController {
  private static final Logger log = LoggerFactory.getLogger(PlatformMailController.class);
  private final MailSettingService settingService;
  private final MailService mailService;
  private final ManagedUserService userService;

  public PlatformMailController(
      MailSettingService settingService, MailService mailService, ManagedUserService userService) {
    this.settingService = settingService;
    this.mailService = mailService;
    this.userService = userService;
  }

  @GetMapping("/admin/mail")
  public String mail(Model model) {
    Optional<MailSetting> setting = settingService.findPlatform();
    if (!model.containsAttribute("mailForm")) {
      model.addAttribute("mailForm", MailSettingForm.from(setting.orElse(null)));
    }
    model.addAttribute("configured", setting.isPresent());
    model.addAttribute("hasSecret", setting.map(MailSetting::hasSecret).orElse(false));
    model.addAttribute("recipientCount", recipients().size());
    return "admin/mail";
  }

  @PostMapping("/admin/mail")
  public String save(
      @Valid @ModelAttribute("mailForm") MailSettingForm mailForm,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirect) {
    if (bindingResult.hasErrors()) {
      Optional<MailSetting> setting = settingService.findPlatform();
      model.addAttribute("configured", setting.isPresent());
      model.addAttribute("hasSecret", setting.map(MailSetting::hasSecret).orElse(false));
      model.addAttribute("recipientCount", recipients().size());
      return "admin/mail";
    }
    settingService.savePlatform(mailForm);
    redirect.addFlashAttribute("message", "SMTP 설정을 저장했습니다.");
    return "redirect:/admin/mail";
  }

  @PostMapping("/admin/mail/test")
  public String test(@RequestParam String testTo, RedirectAttributes redirect) {
    MailSetting setting = settingService.findPlatform().orElse(null);
    if (setting == null) {
      redirect.addFlashAttribute("error", "먼저 SMTP 설정을 저장하세요.");
      return "redirect:/admin/mail";
    }
    try {
      mailService.sendTest(setting, testTo);
      redirect.addFlashAttribute("message", "테스트 메일을 발송했습니다: " + testTo);
    } catch (MailNotAllowedException notAllowed) {
      redirect.addFlashAttribute("error", "테스트 발송 실패: " + notAllowed.getMessage());
    } catch (RuntimeException exception) {
      log.warn("플랫폼 SMTP 테스트 발송 실패", exception);
      redirect.addFlashAttribute("error", "테스트 발송에 실패했습니다. SMTP 설정(호스트/포트/인증)을 확인하세요.");
    }
    return "redirect:/admin/mail";
  }

  @PostMapping("/admin/mail/send")
  public String send(
      @RequestParam String subject,
      @RequestParam String body,
      @RequestParam(defaultValue = "ALL") String recipientMode,
      @RequestParam(required = false) String manualRecipients,
      RedirectAttributes redirect) {
    MailSetting setting = settingService.findPlatform().orElse(null);
    if (setting == null || !setting.isSendable()) {
      redirect.addFlashAttribute("error", "SMTP 설정이 없거나 비활성 상태입니다.");
      return "redirect:/admin/mail";
    }
    List<String> to = "MANUAL".equals(recipientMode) ? parseManual(manualRecipients) : recipients();
    if (to.isEmpty()) {
      redirect.addFlashAttribute("error", "수신자가 없습니다.");
      return "redirect:/admin/mail";
    }
    try {
      MailSendResult result = mailService.sendBulk(setting, to, subject, body);
      redirect.addFlashAttribute("message",
          "발송 완료 — 성공 " + result.sent() + "통, 실패 " + result.failed() + "통");
    } catch (MailNotAllowedException notAllowed) {
      redirect.addFlashAttribute("error", "발송 실패: " + notAllowed.getMessage());
    } catch (RuntimeException exception) {
      log.warn("플랫폼 SMTP 발송 실패", exception);
      redirect.addFlashAttribute("error", "발송에 실패했습니다. SMTP 설정을 확인하세요.");
    }
    return "redirect:/admin/mail";
  }

  /** 플랫폼 발송 대상: 이메일이 등록된 기관 관리자(TENANT_ADMIN). */
  private List<String> recipients() {
    List<String> emails = new ArrayList<>();
    for (ManagedUser user : userService.findByRole(UserRole.TENANT_ADMIN)) {
      if (user.getEmail() != null && !user.getEmail().isBlank()) {
        emails.add(user.getEmail());
      }
    }
    return emails;
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
}
