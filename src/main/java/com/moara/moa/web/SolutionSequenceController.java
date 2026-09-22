package com.moara.moa.web;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.ControlAction;
import com.moara.moa.solution.DuplicateSolutionSequenceException;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.ManagedSolutionService;
import com.moara.moa.solution.SequenceRunResult;
import com.moara.moa.solution.SolutionSequence;
import com.moara.moa.solution.SolutionSequenceForm;
import com.moara.moa.solution.SolutionSequenceRunService;
import com.moara.moa.solution.SolutionSequenceService;
import com.moara.moa.solution.SolutionSequenceStep;
import com.moara.moa.solution.SolutionSequenceStepView;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 솔루션 기동 순서(오케스트레이션) UI(인프라 관리자). 여러 솔루션을 순서대로 시작/역순 중지한다.
 * 서로 다른 서버라도 각 솔루션이 자기 자산으로 원격 실행된다(예: Oracle DB → 리스너 → WAS).
 */
@Controller
public class SolutionSequenceController {
  private final SolutionSequenceService sequenceService;
  private final SolutionSequenceRunService runService;
  private final ManagedSolutionService solutionService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public SolutionSequenceController(
      SolutionSequenceService sequenceService, SolutionSequenceRunService runService,
      ManagedSolutionService solutionService, AuditLogService auditLogService, TenantContext tenantContext) {
    this.sequenceService = sequenceService;
    this.runService = runService;
    this.solutionService = solutionService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/solution-sequences")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!model.containsAttribute("sequenceForm")) {
      model.addAttribute("sequenceForm", new SolutionSequenceForm(null, null));
    }
    List<ManagedSolution> solutions = solutionService.findAll(tenantId);
    Map<UUID, ManagedSolution> solutionById = new HashMap<>();
    for (ManagedSolution s : solutions) {
      solutionById.put(s.getId(), s);
    }
    List<SolutionSequence> sequences = sequenceService.findAll(tenantId);
    Map<UUID, List<SolutionSequenceStepView>> stepsBySequence = new HashMap<>();
    for (SolutionSequence seq : sequences) {
      List<SolutionSequenceStepView> views = new java.util.ArrayList<>();
      int order = 1;
      for (SolutionSequenceStep step : sequenceService.steps(tenantId, seq.getId())) {
        ManagedSolution sol = solutionById.get(step.getSolutionId());
        views.add(new SolutionSequenceStepView(step.getId(), order++,
            sol != null ? sol.getName() : "(삭제됨)", sol != null ? sol.getType().name() : "-",
            step.getAction(), step.getWaitSeconds(), step.isVerifyAfterStart()));
      }
      stepsBySequence.put(seq.getId(), views);
    }
    model.addAttribute("sequences", sequences);
    model.addAttribute("stepsBySequence", stepsBySequence);
    model.addAttribute("solutions", solutions);
    model.addAttribute("actions", com.moara.moa.solution.ControlAction.values());
    model.addAttribute("page", "solution-sequences");
    model.addAttribute("pageTitle", "기동 순서");
    model.addAttribute("projectName", "MOA");
    return "solution-sequences/list";
  }

  @PostMapping("/solution-sequences")
  public String create(
      @Valid @ModelAttribute("sequenceForm") SolutionSequenceForm form, BindingResult binding, Model model) {
    if (binding.hasErrors()) {
      return list(model);
    }
    try {
      SolutionSequence created = sequenceService.create(tenantContext.currentTenantId(), form);
      audit("SEQUENCE_CREATE", created.getId(), created.getName());
      return "redirect:/solutions#seq";
    } catch (DuplicateSolutionSequenceException exception) {
      binding.reject("sequence.duplicate", "이미 사용 중인 순서 이름입니다.");
      return list(model);
    }
  }

  @PostMapping("/solution-sequences/{id}/delete")
  public String delete(@PathVariable UUID id) {
    sequenceService.delete(tenantContext.currentTenantId(), id);
    audit("SEQUENCE_DELETE", id, null);
    return "redirect:/solutions#seq";
  }

  @PostMapping("/solution-sequences/{id}/steps")
  public String addStep(
      @PathVariable UUID id, @RequestParam UUID solutionId,
      @RequestParam(defaultValue = "START") ControlAction action,
      @RequestParam(defaultValue = "0") int waitSeconds,
      @RequestParam(defaultValue = "false") boolean verifyAfterStart) {
    sequenceService.addStep(tenantContext.currentTenantId(), id, solutionId, action, waitSeconds, verifyAfterStart);
    audit("SEQUENCE_STEP_ADD", id, "solution=" + solutionId + " action=" + action);
    return "redirect:/solutions#seq";
  }

  @PostMapping("/solution-sequences/{id}/schedule")
  public String schedule(
      @PathVariable UUID id, @RequestParam(required = false) String forwardCron,
      @RequestParam(required = false) String reverseCron, RedirectAttributes redirect) {
    try {
      sequenceService.setSchedule(tenantContext.currentTenantId(), id, forwardCron, reverseCron);
      audit("SEQUENCE_SCHEDULE", id, "forward=" + forwardCron + " reverse=" + reverseCron);
    } catch (com.moara.moa.solution.InvalidCronException exception) {
      redirect.addFlashAttribute("scheduleError", "잘못된 cron 식입니다(초 분 시 일 월 요일). 예: 0 0 9 * * *");
    }
    return "redirect:/solutions#seq";
  }

  @PostMapping("/solution-sequences/{id}/steps/{stepId}/delete")
  public String removeStep(@PathVariable UUID id, @PathVariable UUID stepId) {
    sequenceService.removeStep(tenantContext.currentTenantId(), stepId);
    audit("SEQUENCE_STEP_REMOVE", id, "step=" + stepId);
    return "redirect:/solutions#seq";
  }

  @PostMapping("/solution-sequences/{id}/steps/{stepId}/move")
  public String moveStep(@PathVariable UUID id, @PathVariable UUID stepId, @RequestParam boolean up) {
    sequenceService.move(tenantContext.currentTenantId(), stepId, up);
    return "redirect:/solutions#seq";
  }

  @PostMapping("/solution-sequences/{id}/run")
  public String runForward(@PathVariable UUID id, RedirectAttributes redirect) {
    runAndFlash(id, false, redirect);
    return "redirect:/solutions#seq";
  }

  @PostMapping("/solution-sequences/{id}/reverse")
  public String runReverse(@PathVariable UUID id, RedirectAttributes redirect) {
    runAndFlash(id, true, redirect);
    return "redirect:/solutions#seq";
  }

  private void runAndFlash(UUID id, boolean reverse, RedirectAttributes redirect) {
    SequenceRunResult result = runService.run(tenantContext.currentTenantId(), id, reverse);
    audit("SEQUENCE_RUN", id, (reverse ? "reverse" : "forward") + " overall=" + result.overallSuccess());
    redirect.addFlashAttribute("runResult", result);
    redirect.addFlashAttribute("runSequenceId", id);
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "SolutionSequence", targetId,
          AuditResult.SUCCESS, message);
    }
  }
}
