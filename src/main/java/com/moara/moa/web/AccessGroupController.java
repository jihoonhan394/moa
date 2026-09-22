package com.moara.moa.web;

import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupForm;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.group.DuplicateAccessGroupException;
import com.moara.moa.group.UserGroupMember;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 그룹 관리 UI. 관리자가 그룹을 만들고 사용자를 멤버로 관리한다.
 * 자산 접근 권한은 권한 메뉴(묶음 권한)로 이관됨(T19). 모든 조회/변경은 현재 테넌트로 스코프된다.
 */
@Controller
public class AccessGroupController {
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public AccessGroupController(
      AccessGroupService groupService,
      ManagedUserService userService,
      AuditLogService auditLogService,
      TenantContext tenantContext) {
    this.groupService = groupService;
    this.userService = userService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/groups")
  public String list(Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    model.addAttribute("tree", groupService.tree(tenantId));
    model.addAttribute("parentOptions", groupService.findAll(tenantId));
    if (!model.containsAttribute("groupForm")) {
      model.addAttribute("groupForm", new AccessGroupForm("", "", null, null));
    }
    return "groups/list";
  }

  @PostMapping("/groups")
  public String create(
      @Valid @ModelAttribute("groupForm") AccessGroupForm groupForm,
      BindingResult bindingResult,
      Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!bindingResult.hasErrors()) {
      try {
        AccessGroup created = groupService.create(tenantId, groupForm);
        audit("GROUP_CREATE", created.getId(), created.getName());
        return "redirect:/groups";
      } catch (DuplicateAccessGroupException exception) {
        bindingResult.rejectValue("name", "group.duplicate", "같은 이름의 그룹이 이미 있습니다.");
      }
    }
    model.addAttribute("tree", groupService.tree(tenantId));
    model.addAttribute("parentOptions", groupService.findAll(tenantId));
    return "groups/list";
  }

  /** 조직 트리에서 그룹의 상위를 이동한다(parentId 비우면 최상위). 순환은 서비스가 거부. */
  @PostMapping("/groups/{id}/move")
  public String move(
      @PathVariable UUID id, @RequestParam(required = false) UUID parentId,
      org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
    try {
      groupService.move(tenantContext.currentTenantId(), id, parentId);
      audit("GROUP_MOVE", id, "parent=" + parentId);
    } catch (IllegalArgumentException exception) {
      redirectAttributes.addFlashAttribute("moveError", exception.getMessage());
    }
    return "redirect:/groups";
  }

  @GetMapping("/groups/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    AccessGroup group = groupService.findById(tenantId, id);

    List<UserGroupMember> members = groupService.findMembers(tenantId, id);
    Map<UUID, ManagedUser> tenantUsers = userService.findByTenant(tenantId).stream()
        .collect(Collectors.toMap(ManagedUser::getId, user -> user));
    Set<UUID> memberIds = members.stream().map(UserGroupMember::getUserId).collect(Collectors.toSet());

    List<MemberView> memberViews = members.stream()
        .map(member -> toMemberView(member, tenantUsers))
        .toList();
    List<ManagedUser> assignableUsers = tenantUsers.values().stream()
        .filter(user -> !memberIds.contains(user.getId()))
        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
        .toList();

    List<AccessGroup> children = groupService.findAll(tenantId).stream()
        .filter(g -> id.equals(g.getParentId()))
        .toList();

    model.addAttribute("group", group);
    model.addAttribute("members", memberViews);
    model.addAttribute("assignableUsers", assignableUsers);
    model.addAttribute("children", children);
    return "groups/detail";
  }

  @PostMapping("/groups/{id}/members")
  public String addMember(@PathVariable UUID id, @RequestParam UUID userId) {
    groupService.addMember(tenantContext.currentTenantId(), id, userId);
    audit("GROUP_ADD_MEMBER", id, "user=" + userId);
    return "redirect:/groups/" + id;
  }

  @PostMapping("/groups/{id}/members/{userId}/delete")
  public String removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
    groupService.removeMember(tenantContext.currentTenantId(), id, userId);
    audit("GROUP_REMOVE_MEMBER", id, "user=" + userId);
    return "redirect:/groups/" + id;
  }

  /** 부서장 지정/해제(멤버여야 함). leader=true 지정, false 해제. */
  @PostMapping("/groups/{id}/members/{userId}/leader")
  public String setLeader(
      @PathVariable UUID id, @PathVariable UUID userId, @RequestParam boolean leader) {
    groupService.setLeader(tenantContext.currentTenantId(), id, userId, leader);
    audit(leader ? "GROUP_SET_LEADER" : "GROUP_UNSET_LEADER", id, "user=" + userId);
    return "redirect:/groups/" + id;
  }

  @PostMapping("/groups/{id}/disable")
  public String disable(@PathVariable UUID id) {
    groupService.disable(tenantContext.currentTenantId(), id);
    audit("GROUP_DISABLE", id, null);
    return "redirect:/groups";
  }

  /** 그룹 이름·설명 수정(상위 이동은 트리 화면의 '상위 이동'으로). */
  @PostMapping("/groups/{id}/edit")
  public String edit(
      @PathVariable UUID id, @RequestParam String name,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) com.moara.moa.group.AccessGroupStatus status,
      org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
    try {
      groupService.update(tenantContext.currentTenantId(), id, new AccessGroupForm(name, description, status, null));
      audit("GROUP_UPDATE", id, name);
    } catch (DuplicateAccessGroupException exception) {
      redirectAttributes.addFlashAttribute("groupError", "같은 이름의 그룹이 이미 있습니다.");
    }
    return "redirect:/groups/" + id;
  }

  /** 그룹 영구 삭제(하위는 상위로 승격, 멤버십 제거). 권한이 부착돼 있으면 삭제 불가 안내. */
  @PostMapping("/groups/{id}/delete")
  public String delete(
      @PathVariable UUID id,
      org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
    try {
      groupService.delete(tenantContext.currentTenantId(), id);
      audit("GROUP_DELETE", id, null);
      return "redirect:/groups";
    } catch (org.springframework.dao.DataIntegrityViolationException exception) {
      redirectAttributes.addFlashAttribute("groupError",
          "이 그룹에 접근 권한(묶음)이 부착돼 있어 삭제할 수 없습니다. 권한 메뉴에서 먼저 분리하거나 '비활성'을 사용하세요.");
      return "redirect:/groups";
    }
  }

  private void audit(String action, UUID targetId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "AccessGroup", targetId, AuditResult.SUCCESS, message);
    }
  }

  private MemberView toMemberView(UserGroupMember member, Map<UUID, ManagedUser> tenantUsers) {
    ManagedUser user = tenantUsers.get(member.getUserId());
    if (user == null) {
      return new MemberView(member.getUserId(), "(알 수 없음)", "", member.isLeader());
    }
    return new MemberView(member.getUserId(), user.getName(), user.getUsername(), member.isLeader());
  }

  public record MemberView(UUID userId, String name, String username, boolean leader) {}
}
