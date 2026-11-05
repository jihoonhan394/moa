package com.moara.moa.invitation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 초대 수락(공개 라우트). 유효한 토큰이면 비밀번호·연락처만 입력받아 계정을 만든다(아이디=이메일).
 * 인증 불필요 — 토큰 자체가 자격이다. 로그인은 필요 없다.
 */
@Controller
public class InviteAcceptController {
  private static final Logger log = LoggerFactory.getLogger(InviteAcceptController.class);

  private final InvitationService invitationService;

  public InviteAcceptController(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @GetMapping("/invite/{token}")
  public String form(@PathVariable String token, Model model) {
    try {
      UserInvitation inv = invitationService.acceptable(token);
      model.addAttribute("email", inv.getEmail());
      model.addAttribute("name", inv.getName());
      model.addAttribute("token", token);
      return "invite/accept";
    } catch (InvitationInvalidException exception) {
      return "invite/invalid";
    }
  }

  @PostMapping("/invite/{token}")
  public String accept(
      @PathVariable String token,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String phone,
      @RequestParam String password,
      @RequestParam String passwordConfirm,
      Model model) {
    // 재표시용 컨텍스트(오류 시).
    try {
      UserInvitation inv = invitationService.acceptable(token);
      model.addAttribute("email", inv.getEmail());
      model.addAttribute("name", name != null ? name : inv.getName());
      model.addAttribute("token", token);
    } catch (InvitationInvalidException exception) {
      return "invite/invalid";
    }
    if (password == null || !password.equals(passwordConfirm)) {
      model.addAttribute("error", "비밀번호가 일치하지 않습니다.");
      return "invite/accept";
    }
    try {
      invitationService.accept(token, name, phone, password);
    } catch (InvitationInvalidException exception) {
      return "invite/invalid";
    } catch (RuntimeException exception) {
      // ⚠️ exception.getMessage() 를 그대로 보여 주면 SQL·제약 이름 같은 내부 사정이 가입 화면에
      // 그대로 나간다(실제로 "Unique index or primary key violation: CONSTRAINT_F8 ..." 가 나왔다).
      // 서버 로그에만 남기고 사용자에게는 할 수 있는 일을 알려 준다.
      log.warn("초대 수락 실패(token={}): {}", token, exception.toString());
      model.addAttribute("error", "가입 처리에 실패했습니다. 잠시 후 다시 시도하거나 담당자에게 문의해 주세요.");
      return "invite/accept";
    }
    return "redirect:/login?joined";
  }
}
