package com.moara.moa.web;

import com.moara.moa.mail.MailSendResult;
import com.moara.moa.mail.MailService;
import com.moara.moa.mail.MailSetting;
import com.moara.moa.mail.MailSettingService;
import com.moara.moa.notice.Notice;
import com.moara.moa.notice.NoticeForm;
import com.moara.moa.notice.NoticeService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 기관 공지사항. 열람은 기관 전원(USER 포함), 작성·수정·삭제는 기관 관리자(TENANT_ADMIN)만 —
 * 쓰기 라우트 제한은 SecurityConfig가 강제한다. 모든 조회/수정은 현재 기관으로 격리된다.
 */
@Controller
public class NoticeController {
  private final NoticeService noticeService;
  private final TenantContext tenantContext;
  private final MailService mailService;
  private final MailSettingService mailSettingService;
  private final ManagedUserService userService;

  public NoticeController(
      NoticeService noticeService,
      TenantContext tenantContext,
      MailService mailService,
      MailSettingService mailSettingService,
      ManagedUserService userService) {
    this.noticeService = noticeService;
    this.tenantContext = tenantContext;
    this.mailService = mailService;
    this.mailSettingService = mailSettingService;
    this.userService = userService;
  }

  @GetMapping("/notices")
  public String list(Model model) {
    model.addAttribute("notices", noticeService.list(tenantContext.currentTenantId()));
    return "notices/list";
  }

  @GetMapping("/notices/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    model.addAttribute("notice", noticeService.get(tenantContext.currentTenantId(), id));
    return "notices/detail";
  }

  @GetMapping("/notices/new")
  public String createForm(Model model) {
    if (!model.containsAttribute("noticeForm")) {
      model.addAttribute("noticeForm", new NoticeForm("", "", false, false));
    }
    model.addAttribute("mode", "create");
    return "notices/form";
  }

  @PostMapping("/notices")
  public String create(
      @Valid @ModelAttribute("noticeForm") NoticeForm noticeForm,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirect) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("mode", "create");
      return "notices/form";
    }
    UUID tenantId = tenantContext.currentTenantId();
    Notice notice = noticeService.create(tenantId, noticeForm, tenantContext.currentUserId(), currentName());
    redirect.addFlashAttribute("message", "공지를 등록했습니다.");
    if (noticeForm.emailToUsers()) {
      emailNotice(tenantId, notice, redirect);
    }
    return "redirect:/notices";
  }

  @GetMapping("/notices/{id}/edit")
  public String editForm(@PathVariable UUID id, Model model) {
    Notice notice = noticeService.get(tenantContext.currentTenantId(), id);
    if (!model.containsAttribute("noticeForm")) {
      model.addAttribute("noticeForm",
          new NoticeForm(notice.getTitle(), notice.getBody(), notice.isPinned(), false));
    }
    model.addAttribute("mode", "edit");
    model.addAttribute("noticeId", id);
    return "notices/form";
  }

  @PostMapping("/notices/{id}")
  public String update(
      @PathVariable UUID id,
      @Valid @ModelAttribute("noticeForm") NoticeForm noticeForm,
      BindingResult bindingResult,
      Model model,
      RedirectAttributes redirect) {
    if (bindingResult.hasErrors()) {
      model.addAttribute("mode", "edit");
      model.addAttribute("noticeId", id);
      return "notices/form";
    }
    noticeService.update(tenantContext.currentTenantId(), id, noticeForm);
    redirect.addFlashAttribute("message", "공지를 수정했습니다.");
    return "redirect:/notices";
  }

  @PostMapping("/notices/{id}/delete")
  public String delete(@PathVariable UUID id, RedirectAttributes redirect) {
    noticeService.delete(tenantContext.currentTenantId(), id);
    redirect.addFlashAttribute("message", "공지를 삭제했습니다.");
    return "redirect:/notices";
  }

  /** 공지를 기관 SMTP로 활성 사용자에게 이메일 발송하고 결과를 플래시로 알린다(설정 없으면 안내만). */
  private void emailNotice(UUID tenantId, Notice notice, RedirectAttributes redirect) {
    MailSetting setting = mailSettingService.findForTenant(tenantId).orElse(null);
    if (setting == null || !setting.isSendable()) {
      redirect.addFlashAttribute("error", "이메일 발송은 건너뜀 — 기관 SMTP가 설정/활성화되어 있지 않습니다.");
      return;
    }
    List<String> to = userService.activeUserEmails(tenantId);
    if (to.isEmpty()) {
      redirect.addFlashAttribute("error", "이메일 발송 대상(이메일 보유 사용자)이 없습니다.");
      return;
    }
    try {
      MailSendResult result = mailService.sendBulk(setting, to, "[공지] " + notice.getTitle(), notice.getBody());
      redirect.addFlashAttribute("mailMessage",
          "공지 이메일 발송 — 성공 " + result.sent() + "통, 실패 " + result.failed() + "통");
    } catch (RuntimeException exception) {
      redirect.addFlashAttribute("error", "공지 이메일 발송 실패했습니다.");
    }
  }

  private String currentName() {
    MoaUserDetails user = tenantContext.currentUser();
    return user == null ? null : user.getUsername();
  }
}
