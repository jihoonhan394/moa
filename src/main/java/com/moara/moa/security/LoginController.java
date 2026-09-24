package com.moara.moa.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

  /**
   * 진입 2단계: 로그인 폼. 1단계(기관 선택)를 거치지 않았으면 진입 화면으로 되돌린다.
   * 선택된 기관명(또는 플랫폼 여부)을 화면에 표시한다.
   */
  @GetMapping("/login")
  public String login(HttpServletRequest request, Model model) {
    HttpSession session = request.getSession(false);
    if (!EntrySession.hasSelection(session)) {
      return "redirect:/enter";
    }
    if (EntrySession.isPlatform(session)) {
      model.addAttribute("platform", true);
    } else {
      model.addAttribute("tenantName", EntrySession.tenantName(session));
      model.addAttribute("tenantCode", EntrySession.tenantCode(session));
    }
    return "login";
  }
}
