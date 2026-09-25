package com.moara.moa.deputy;

import com.moara.moa.audit.TenantAuditRecorder;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 팀 내 대직(임시 부재 권한) — 부서장이 <b>자기 팀 안에서</b> "부재 팀원의 운영을 대직자가 대신"을 기간제로
 * 지정한다. 부재자·대직자 <b>둘 다 부서장이 이끄는 팀원</b>이어야 한다(스코프 강제). 창 기간 동안 대직자는
 * 부재자의 서버 접근·솔루션 제어를 상속하고, 종료일이 지나면 자동 원복(계산형).
 */
@Controller
public class TeamDeputyController {
  private final DeputyService deputyService;
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final TenantAuditRecorder auditRecorder;
  private final TenantContext tenantContext;

  public TeamDeputyController(
      DeputyService deputyService, AccessGroupService groupService, ManagedUserService userService,
      TenantAuditRecorder auditRecorder, TenantContext tenantContext) {
    this.deputyService = deputyService;
    this.groupService = groupService;
    this.userService = userService;
    this.auditRecorder = auditRecorder;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/team/deputies")
  public String index(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID me = tenantContext.currentUserId();
    Set<UUID> teamIds = groupService.teamMemberIds(tenantId, me);

    // 한 번 읽어 팀원 목록과 이름 맵에 함께 쓴다(전에는 같은 질의를 두 번 했다).
    List<ManagedUser> tenantUsers = userService.findByTenant(tenantId);
    List<ManagedUser> teamUsers =
        tenantUsers.stream().filter(u -> teamIds.contains(u.getId())).toList();
    Map<UUID, String> userNames = new LinkedHashMap<>();
    for (ManagedUser u : tenantUsers) {
      userNames.put(u.getId(), ManagedUserService.label(u));
    }

    // 내 팀과 관련된 대직만 표시(부재자 또는 대직자가 내 팀원).
    LocalDate today = LocalDate.now();
    List<DeputyDelegation> mine = deputyService.findAll(tenantId).stream()
        .filter(d -> teamIds.contains(d.getAbsentUserId()) || teamIds.contains(d.getDeputyUserId()))
        .toList();

    model.addAttribute("delegations", mine);
    model.addAttribute("teamUsers", teamUsers);
    model.addAttribute("userNames", userNames);
    model.addAttribute("today", today);
    model.addAttribute("page", "team");
    model.addAttribute("pageTitle", "팀 내 대직");
    model.addAttribute("projectName", "MOA");
    return "team/deputies";
  }

  @PostMapping("/team/deputies")
  public String create(
      @RequestParam UUID absentUserId, @RequestParam UUID deputyUserId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsOn,
      RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID me = tenantContext.currentUserId();
    if (absentUserId.equals(deputyUserId)) {
      redirect.addFlashAttribute("teamError", "부재자와 대직자는 서로 달라야 합니다.");
      return "redirect:/team/deputies";
    }
    if (!groupService.leads(tenantId, me, absentUserId) || !groupService.leads(tenantId, me, deputyUserId)) {
      redirect.addFlashAttribute("teamError", "부재자·대직자 모두 본인이 이끄는 팀원이어야 합니다.");
      return "redirect:/team/deputies";
    }
    if (endsOn.isBefore(startsOn)) {
      redirect.addFlashAttribute("teamError", "종료일이 시작일보다 빠를 수 없습니다.");
      return "redirect:/team/deputies";
    }
    deputyService.create(tenantId, absentUserId, deputyUserId, startsOn, endsOn, me);
    audit("TEAM_DEPUTY_CREATE", absentUserId, "deputy=" + deputyUserId + " " + startsOn + "~" + endsOn);
    redirect.addFlashAttribute("teamOk", "대직을 지정했습니다.");
    return "redirect:/team/deputies";
  }

  @PostMapping("/team/deputies/{id}/end")
  public String end(@PathVariable UUID id, RedirectAttributes redirect) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID me = tenantContext.currentUserId();
    // 내 팀 관련 대직만 종료 가능.
    boolean mine = deputyService.findAll(tenantId).stream()
        .anyMatch(d -> d.getId().equals(id)
            && (groupService.leads(tenantId, me, d.getAbsentUserId())
                || groupService.leads(tenantId, me, d.getDeputyUserId())));
    if (!mine) {
      redirect.addFlashAttribute("teamError", "본인 팀의 대직만 종료할 수 있습니다.");
      return "redirect:/team/deputies";
    }
    deputyService.end(tenantId, id);
    audit("TEAM_DEPUTY_END", id, null);
    redirect.addFlashAttribute("teamOk", "대직을 종료했습니다.");
    return "redirect:/team/deputies";
  }

  /** 특권 행위 기록. 정책(행위자 없으면 미기록 등)은 TenantAuditRecorder에 있다. */
  private void audit(String action, UUID targetId, String message) {
    auditRecorder.record("DeputyDelegation", action, targetId, message);
  }
}
