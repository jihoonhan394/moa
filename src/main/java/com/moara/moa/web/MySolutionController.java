package com.moara.moa.web;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.ControlAction;
import com.moara.moa.solution.ControlResult;
import com.moara.moa.solution.SolutionAccessService;
import com.moara.moa.solution.SolutionControlService;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 일반 사용자용 '내 솔루션' 제어. 배정받은 솔루션만 start/stop/status 할 수 있다(등록·유지보수는
 * 인프라 관리자 몫). 제어는 배정 여부로 인가하며, 모든 행위를 감사로 남긴다.
 */
@Controller
public class MySolutionController {
  private final SolutionAccessService accessService;
  private final SolutionControlService controlService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public MySolutionController(
      SolutionAccessService accessService, SolutionControlService controlService,
      AuditLogService auditLogService, TenantContext tenantContext) {
    this.accessService = accessService;
    this.controlService = controlService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/my-solutions")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("solutions", accessService.assignedSolutions(tenantId, tenantContext.currentUserId()));
    model.addAttribute("actions", ControlAction.values());
    return "my-solutions";
  }

  @PostMapping("/my-solutions/{id}/control")
  public String control(
      @PathVariable UUID id, @RequestParam ControlAction action, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = tenantContext.currentUserId();
    // 배정받지 않은 솔루션은 제어할 수 없다(인가). 화면엔 배정된 것만 보이므로 미배정 요청은 위·변조로 간주해 거부.
    if (!accessService.canControl(tenantId, id, userId)) {
      throw new AccessDeniedException("이 솔루션을 제어할 권한이 없습니다.");
    }
    try {
      ControlResult result = controlService.control(tenantId, id, action);
      audit(userId, tenantId, id, action + " success=" + result.success());
      redirect.addFlashAttribute("controlMessage",
          action + " → " + (result.success() ? "성공" : "실패") + " : " + result.output());
    } catch (RuntimeException exception) {
      audit(userId, tenantId, id, action + " error");
      redirect.addFlashAttribute("controlError", action + " 실패했습니다.");
    }
    return "redirect:/my-solutions";
  }

  private void audit(UUID actorId, UUID tenantId, UUID solutionId, String message) {
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantId, actorId, "SOLUTION_CONTROL", "ManagedSolution", solutionId, AuditResult.SUCCESS, message);
    }
  }
}
