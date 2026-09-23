package com.moara.moa.web;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import com.moara.moa.security.TwoFactorService;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 2단계 인증(TOTP / Google Authenticator) 설정 — 토대. 로그인 사용자 본인이 등록·확인·해제한다.
 * 아직 로그인/민감 작업에 강제하지 않는다(후속 단계에서 게이팅 연결).
 */
@Controller
public class TwoFactorController {
  private final TwoFactorService twoFactorService;
  private final TenantContext tenantContext;
  private final AuditLogService auditLogService;

  public TwoFactorController(
      TwoFactorService twoFactorService, TenantContext tenantContext, AuditLogService auditLogService) {
    this.twoFactorService = twoFactorService;
    this.tenantContext = tenantContext;
    this.auditLogService = auditLogService;
  }

  @GetMapping("/security/2fa")
  public String index(Model model) {
    model.addAttribute("enabled", twoFactorService.isEnabled(tenantContext.currentUserId()));
    model.addAttribute("page", "two-factor");
    return "security/2fa";
  }

  /** 등록 시작: 시크릿·URI를 만들어 화면에 표시(인증기 앱 등록용), 코드 확인 폼 노출. */
  @PostMapping("/security/2fa/start")
  public String start(Model model) {
    UUID userId = tenantContext.currentUserId();
    MoaUserDetails user = tenantContext.currentUser();
    String account = user != null ? user.getUsername() : "user";
    TwoFactorService.Enrollment enrollment =
        twoFactorService.startEnrollment(tenantContext.currentTenantId(), userId, account);
    audit("TWO_FACTOR_ENROLL_START", AuditResult.SUCCESS, "2FA 등록 시작");
    model.addAttribute("enrollment", enrollment);
    model.addAttribute("enabled", false);
    model.addAttribute("page", "two-factor");
    return "security/2fa";
  }

  @PostMapping("/security/2fa/confirm")
  public String confirm(@RequestParam String code, Model model) {
    boolean ok = twoFactorService.confirm(tenantContext.currentUserId(), code);
    // 실패도 기록한다 — 코드 오입력 반복은 공격 신호일 수 있다.
    audit("TWO_FACTOR_ENABLE", ok ? AuditResult.SUCCESS : AuditResult.FAILURE,
        ok ? "2FA 활성화" : "2FA 코드 확인 실패");
    model.addAttribute("enabled", twoFactorService.isEnabled(tenantContext.currentUserId()));
    if (!ok) {
      model.addAttribute("confirmError", "코드가 올바르지 않습니다. 다시 등록을 시작하고 앱의 6자리 코드를 입력하세요.");
    }
    model.addAttribute("page", "two-factor");
    return "security/2fa";
  }

  @PostMapping("/security/2fa/disable")
  public String disable() {
    twoFactorService.disable(tenantContext.currentUserId());
    audit("TWO_FACTOR_DISABLE", AuditResult.SUCCESS, "2FA 비활성화");
    return "redirect:/security/2fa";
  }

  /** 2FA 변경은 본인 계정 대상이므로 actor와 target이 같다. */
  private void audit(String action, AuditResult result, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "ManagedUser", actorId, result, message);
    }
  }
}
