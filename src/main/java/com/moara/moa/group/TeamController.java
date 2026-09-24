package com.moara.moa.group;

import com.moara.moa.inventory.InventoryItem;
import com.moara.moa.inventory.InventoryItemService;
import com.moara.moa.onboarding.OnboardingService;
import com.moara.moa.security.MoaUserDetails;
import com.moara.moa.security.TenantContext;
import com.moara.moa.solution.ManagedSolution;
import com.moara.moa.solution.SolutionAccessService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserNotFoundException;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import com.moara.moa.wiki.WikiSpace;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 부서장 팀 관리 콘솔 — 한 화면에서 (1) 팀원 접근 현황 조회(재인증), (2) 온보딩 템플릿 적용,
 * (3) 역할 위임을 한다. 인증 라우트로 열되 모든 작업 대상은 <b>본인이 이끄는 그룹의 팀원으로 한정</b>한다
 * (기관 관리자는 감독용으로 전체 대상). 위임 원칙: "자기가 가진 관리 역할만 팀원에게 위임할 수 있다"
 * — 역할이 한 사람에게 뭉친 소규모 회사에서도 자연히 동작한다(가진 권한만 나눠주므로).
 */
@Controller
public class TeamController {
  private static final Set<UserRole> DELEGATABLE = EnumSet.of(UserRole.INFRA_MANAGER, UserRole.ASSET_MANAGER);

  private final OnboardingService onboardingService;
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final SolutionAccessService solutionAccessService;
  private final InventoryItemService inventoryService;
  private final com.moara.moa.wiki.WikiSpaceService wikiSpaceService;
  private final TenantContext tenantContext;

  public TeamController(
      OnboardingService onboardingService, AccessGroupService groupService, ManagedUserService userService,
      SolutionAccessService solutionAccessService, InventoryItemService inventoryService,
      com.moara.moa.wiki.WikiSpaceService wikiSpaceService, TenantContext tenantContext) {
    this.onboardingService = onboardingService;
    this.groupService = groupService;
    this.userService = userService;
    this.solutionAccessService = solutionAccessService;
    this.inventoryService = inventoryService;
    this.wikiSpaceService = wikiSpaceService;
    this.tenantContext = tenantContext;
  }

  /** 팀원 접근 현황 한 줄(재인증 뷰). */
  public record TeamMemberView(
      ManagedUser user, List<String> solutions, List<String> assets, List<String> spaces, Set<UserRole> roles) {}

  @GetMapping("/team")
  public String index(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("members", memberViews(tenantId));
    model.addAttribute("templates", onboardingService.templates(tenantId));
    model.addAttribute("ledGroups", groupService.groupsLedBy(tenantId, tenantContext.currentUserId()));
    model.addAttribute("delegatableRoles", delegatableRoles());
    model.addAttribute("isAdmin", isTenantAdmin());
    model.addAttribute("page", "team");
    return "team/console";
  }

  @PostMapping("/team/apply")
  public String apply(
      @RequestParam UUID templateId, @RequestParam UUID userId, RedirectAttributes redirectAttributes) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, userId)) {
      redirectAttributes.addFlashAttribute("teamError", "본인이 이끄는 부서의 팀원에게만 적용할 수 있습니다.");
      return "redirect:/team";
    }
    onboardingService.apply(tenantId, userId, templateId);
    redirectAttributes.addFlashAttribute("teamOk", "온보딩을 적용했습니다.");
    return "redirect:/team";
  }

  /** 역할 위임: 팀원에게 '내가 가진' 관리 역할을 부여/회수한다. */
  @PostMapping("/team/roles")
  public String delegateRole(
      @RequestParam UUID userId, @RequestParam UserRole role, @RequestParam boolean grant,
      RedirectAttributes redirectAttributes) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!canManage(tenantId, userId)) {
      redirectAttributes.addFlashAttribute("teamError", "본인 팀원에게만 위임할 수 있습니다.");
      return "redirect:/team";
    }
    if (!delegatableRoles().contains(role)) {
      redirectAttributes.addFlashAttribute("teamError", "자신이 보유한 관리 역할만 위임할 수 있습니다.");
      return "redirect:/team";
    }
    ManagedUser member = userService.findById(tenantId, userId);
    Set<UserRole> newRoles = new java.util.HashSet<>(member.getRoles());
    if (grant) {
      newRoles.add(role);
    } else {
      newRoles.remove(role);
    }
    userService.assignRoles(tenantId, userId, newRoles);
    redirectAttributes.addFlashAttribute("teamOk",
        (grant ? "위임: " : "회수: ") + member.getName() + " → " + role.getLabel());
    return "redirect:/team";
  }

  /** 위임 가능한 역할: 관리자면 인프라·자산, 부서장이면 '자기가 보유한' 관리 역할만. */
  private Set<UserRole> delegatableRoles() {
    if (isTenantAdmin()) {
      return DELEGATABLE;
    }
    MoaUserDetails me = tenantContext.currentUser();
    if (me == null) {
      return Set.of();
    }
    return me.getRoles().stream().filter(DELEGATABLE::contains).collect(Collectors.toSet());
  }

  private List<TeamMemberView> memberViews(UUID tenantId) {
    List<TeamMemberView> views = new ArrayList<>();
    for (ManagedUser member : candidates(tenantId)) {
      List<String> solutions = solutionAccessService.assignedSolutions(tenantId, member.getId()).stream()
          .map(ManagedSolution::getName).toList();
      List<String> assets = inventoryService.findAssignedTo(tenantId, member.getId()).stream()
          .map(InventoryItem::getName).toList();
      List<String> spaces = wikiSpaceService.findGrantedToUser(tenantId, member.getId()).stream()
          .map(WikiSpace::getName).toList();
      views.add(new TeamMemberView(member, solutions, assets, spaces, member.getRoles()));
    }
    return views;
  }

  /** 대상 후보: <b>본인이 이끄는 그룹(부서)의 팀원만</b>. 기관 관리자라도 팀 콘솔은 자기 부서로 스코프한다
   * (전체 사용자 관리는 /users). 이끄는 부서가 없으면 빈 목록. */
  private List<ManagedUser> candidates(UUID tenantId) {
    Set<UUID> teamIds = groupService.teamMemberIds(tenantId, tenantContext.currentUserId());
    return userService.findByTenant(tenantId).stream()
        .filter(user -> teamIds.contains(user.getId()))
        .toList();
  }

  /**
   * 대상 사용자를 관리할 수 있는가. <b>테넌트 소속 확인을 분기 밖에서 무조건 수행</b>한다 —
   * {@code isTenantAdmin() || leads(...)} 형태로 두면 관리자일 때 단락되어 테넌트 검증이
   * 통째로 건너뛰어지고, 모든 기관에 관리자가 있으므로 교차 테넌트 조작이 열린다.
   */
  private boolean canManage(UUID tenantId, UUID targetUserId) {
    if (!belongsToTenant(tenantId, targetUserId)) {
      return false;
    }
    return isTenantAdmin() || groupService.leads(tenantId, tenantContext.currentUserId(), targetUserId);
  }

  private boolean belongsToTenant(UUID tenantId, UUID targetUserId) {
    try {
      userService.findById(tenantId, targetUserId);
      return true;
    } catch (ManagedUserNotFoundException notFound) {
      return false;
    }
  }

  private boolean isTenantAdmin() {
    MoaUserDetails user = tenantContext.currentUser();
    return user != null && user.hasRole(UserRole.TENANT_ADMIN);
  }
}
