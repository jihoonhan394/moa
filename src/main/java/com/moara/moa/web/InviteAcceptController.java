package com.moara.moa.web;

import com.moara.moa.invitation.InvitationInvalidException;
import com.moara.moa.invitation.InvitationService;
import com.moara.moa.invitation.UserInvitation;
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
      @RequestParam String phone,
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
      model.addAttribute("error", "가입 처리에 실패했습니다: " + exception.getMessage());
      return "invite/accept";
    }
    return "redirect:/login?joined";
  }
}
