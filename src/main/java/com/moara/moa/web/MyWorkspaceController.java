package com.moara.moa.web;

import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.UserGroupMember;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.notice.NoticeService;
import com.moara.moa.onboarding.OnboardingService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.SolutionAccessService;
import com.moara.moa.wiki.WikiSpaceService;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * '내 워크스페이스' — 로그인 사용자(특히 신입)가 자기에게 배정된 자원과 온보딩 체크리스트를 한 화면에서 본다.
 * 모두 <b>읽기 전용 소비 뷰</b>다: 자산 배정은 경영지원팀, 솔루션 배정은 인프라 관리자가 관리하고
 * 여기서는 '내 몫'만 조회한다(관리 책임과 소비 뷰의 분리).
 */
@Controller
public class MyWorkspaceController {
  private final InventoryItemService inventoryService;
  private final SolutionAccessService solutionAccessService;
  private final WikiSpaceService wikiSpaceService;
  private final OnboardingService onboardingService;
  private final NoticeService noticeService;
  private final AccessGroupService groupService;
  private final TenantContext tenantContext;

  public MyWorkspaceController(
      InventoryItemService inventoryService, SolutionAccessService solutionAccessService,
      WikiSpaceService wikiSpaceService, OnboardingService onboardingService,
      NoticeService noticeService, AccessGroupService groupService, TenantContext tenantContext) {
    this.inventoryService = inventoryService;
    this.solutionAccessService = solutionAccessService;
    this.wikiSpaceService = wikiSpaceService;
    this.onboardingService = onboardingService;
    this.noticeService = noticeService;
    this.groupService = groupService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/my/workspace")
  public String workspace(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    UUID userId = tenantContext.currentUserId();
    model.addAttribute("myAssets", inventoryService.findAssignedTo(tenantId, userId));
    // 우리 팀이 소유한 자산(그룹 소유). 개인 배정과 별개로 팀 자산을 조회한다.
    Set<UUID> myGroups = groupService.findGroupsOfUser(tenantId, userId).stream()
        .map(UserGroupMember::getGroupId).collect(Collectors.toSet());
    model.addAttribute("teamAssets", inventoryService.findOwnedByGroups(tenantId, myGroups));
    model.addAttribute("mySolutions", solutionAccessService.assignedSolutions(tenantId, userId));
    model.addAttribute("mySpaces", wikiSpaceService.findGrantedToUser(tenantId, userId));
    model.addAttribute("tasks", onboardingService.myTasks(tenantId, userId));
    model.addAttribute("progress", onboardingService.progressPercent(tenantId, userId));
    model.addAttribute("notices", noticeService.list(tenantId).stream().limit(5).toList());
    model.addAttribute("page", "my-workspace");
    return "my/workspace";
  }

  @PostMapping("/my/workspace/tasks/{id}/done")
  public String markDone(@PathVariable UUID id) {
    onboardingService.markDone(tenantContext.currentTenantId(), tenantContext.currentUserId(), id);
    return "redirect:/my/workspace";
  }
}
