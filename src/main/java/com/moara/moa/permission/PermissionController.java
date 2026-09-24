package com.moara.moa.permission;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 * 묶음 권한(Permission Set) 관리 UI. 권한을 만들어 엔트리(자산×액션)를 담고,
 * 그룹(주력)/사용자(일시 예외, 만료)에 부착한다. 접근 판정은 이 모델을 근거로 한다(T18).
 * 모든 조회/변경은 현재 테넌트로 스코프되며 관리자 전용(SecurityConfig)이다.
 */
@Controller
public class PermissionController {
  private final PermissionSetService permissionService;
  private final AssetService assetService;
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public PermissionController(
      PermissionSetService permissionService,
      AssetService assetService,
      AccessGroupService groupService,
      ManagedUserService userService,
      AuditLogService auditLogService,
      TenantContext tenantContext) {
    this.permissionService = permissionService;
    this.assetService = assetService;
    this.groupService = groupService;
    this.userService = userService;
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/permissions")
  public String list(Model model) {
    model.addAttribute("permissions", permissionService.findAll(tenantContext.currentTenantId()));
    if (!model.containsAttribute("permissionForm")) {
      model.addAttribute("permissionForm", new PermissionForm("", "", PermissionStatus.ACTIVE));
    }
    model.addAttribute("page", "permissions");
    model.addAttribute("pageTitle", "권한 관리");
    model.addAttribute("projectName", "MOA");
    return "permissions/list";
  }

  @PostMapping("/permissions")
  public String create(
      @Valid @ModelAttribute("permissionForm") PermissionForm permissionForm,
      BindingResult bindingResult,
      Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    if (!bindingResult.hasErrors()) {
      try {
        Permission created = permissionService.create(tenantId, permissionForm);
        audit("PERMISSION_CREATE", created.getId(), created.getName());
        return "redirect:/permissions/" + created.getId();
      } catch (DuplicatePermissionException exception) {
        bindingResult.rejectValue("name", "permission.duplicate", "같은 이름의 권한이 이미 있습니다.");
      }
    }
    model.addAttribute("permissions", permissionService.findAll(tenantId));
    model.addAttribute("page", "permissions");
    model.addAttribute("pageTitle", "권한 관리");
    model.addAttribute("projectName", "MOA");
    return "permissions/list";
  }

  @GetMapping("/permissions/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    UUID tenantId = tenantContext.currentTenantId();
    Permission permission = permissionService.findById(tenantId, id);

    List<Asset> assets = assetService.findAll(tenantId);
    Map<UUID, Asset> assetById = assets.stream().collect(Collectors.toMap(Asset::getId, asset -> asset));
    List<EntryView> entries = permissionService.findEntries(tenantId, id).stream()
        .map(entry -> toEntryView(entry, assetById))
        .toList();

    Map<UUID, AccessGroup> groupById = groupService.findAll(tenantId).stream()
        .collect(Collectors.toMap(AccessGroup::getId, group -> group));
    List<PermissionGroupAssignment> groupAssignments = permissionService.findGroupAssignments(tenantId, id);
    Set<UUID> assignedGroupIds = groupAssignments.stream()
        .map(PermissionGroupAssignment::getGroupId).collect(Collectors.toSet());
    List<GroupAssignView> groupViews = groupAssignments.stream()
        .map(assignment -> new GroupAssignView(assignment.getGroupId(), groupName(groupById, assignment.getGroupId())))
        .toList();

    Map<UUID, ManagedUser> userById = userService.findByTenant(tenantId).stream()
        .collect(Collectors.toMap(ManagedUser::getId, user -> user));
    List<PermissionUserAssignment> userAssignments = permissionService.findUserAssignments(tenantId, id);
    Set<UUID> assignedUserIds = userAssignments.stream()
        .map(PermissionUserAssignment::getUserId).collect(Collectors.toSet());
    List<UserAssignView> userViews = userAssignments.stream()
        .map(assignment -> toUserAssignView(assignment, userById))
        .toList();

    model.addAttribute("permission", permission);
    model.addAttribute("entries", entries);
    model.addAttribute("groupAssignments", groupViews);
    model.addAttribute("userAssignments", userViews);
    model.addAttribute("assets", assets);
    model.addAttribute("actions", PermissionProtocol.values());
    model.addAttribute("assignableGroups", groupById.values().stream()
        .filter(group -> !assignedGroupIds.contains(group.getId()))
        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList());
    model.addAttribute("assignableUsers", userById.values().stream()
        .filter(user -> !assignedUserIds.contains(user.getId()))
        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList());
    model.addAttribute("statuses", PermissionStatus.values());
    model.addAttribute("page", "permissions");
    model.addAttribute("pageTitle", "권한 상세");
    model.addAttribute("projectName", "MOA");
    return "permissions/detail";
  }

  @PostMapping("/permissions/{id}")
  public String update(@PathVariable UUID id, @RequestParam String name,
      @RequestParam(required = false) String description, @RequestParam PermissionStatus status) {
    permissionService.update(tenantContext.currentTenantId(), id, new PermissionForm(name, description, status));
    audit("PERMISSION_UPDATE", id, name);
    return "redirect:/permissions/" + id;
  }

  @PostMapping("/permissions/{id}/entries")
  public String addEntry(
      @PathVariable UUID id, @RequestParam UUID assetId, @RequestParam PermissionProtocol action) {
    permissionService.addEntry(tenantContext.currentTenantId(), id, assetId, action);
    audit("PERMISSION_ENTRY_ADD", id, "asset=" + assetId + ", action=" + action);
    return "redirect:/permissions/" + id;
  }

  @PostMapping("/permissions/{id}/entries/delete")
  public String removeEntry(
      @PathVariable UUID id, @RequestParam UUID assetId, @RequestParam PermissionProtocol action) {
    permissionService.removeEntry(tenantContext.currentTenantId(), id, assetId, action);
    audit("PERMISSION_ENTRY_REMOVE", id, "asset=" + assetId + ", action=" + action);
    return "redirect:/permissions/" + id;
  }

  @PostMapping("/permissions/{id}/groups")
  public String assignGroup(@PathVariable UUID id, @RequestParam UUID groupId) {
    permissionService.assignToGroup(tenantContext.currentTenantId(), id, groupId);
    audit("PERMISSION_ASSIGN_GROUP", id, "group=" + groupId);
    return "redirect:/permissions/" + id;
  }

  @PostMapping("/permissions/{id}/groups/delete")
  public String unassignGroup(@PathVariable UUID id, @RequestParam UUID groupId) {
    permissionService.unassignGroup(tenantContext.currentTenantId(), id, groupId);
    audit("PERMISSION_UNASSIGN_GROUP", id, "group=" + groupId);
    return "redirect:/permissions/" + id;
  }

  @PostMapping("/permissions/{id}/users")
  public String assignUser(
      @PathVariable UUID id, @RequestParam UUID userId, @RequestParam(required = false) String expiresAt) {
    permissionService.assignToUser(tenantContext.currentTenantId(), id, userId, parseExpiry(expiresAt));
    audit("PERMISSION_ASSIGN_USER", id, "user=" + userId + ", expires=" + (expiresAt == null ? "-" : expiresAt));
    return "redirect:/permissions/" + id;
  }

  @PostMapping("/permissions/{id}/users/delete")
  public String unassignUser(@PathVariable UUID id, @RequestParam UUID userId) {
    permissionService.unassignUser(tenantContext.currentTenantId(), id, userId);
    audit("PERMISSION_UNASSIGN_USER", id, "user=" + userId);
    return "redirect:/permissions/" + id;
  }

  /** datetime-local(빈값=무기한)을 시스템 존 기준 OffsetDateTime으로 파싱. */
  private OffsetDateTime parseExpiry(String expiresAt) {
    if (expiresAt == null || expiresAt.isBlank()) {
      return null;
    }
    return LocalDateTime.parse(expiresAt).atZone(ZoneId.systemDefault()).toOffsetDateTime();
  }

  private void audit(String action, UUID permissionId, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId != null) {
      auditLogService.recordTenantAction(
          tenantContext.currentTenantId(), actorId, action, "Permission", permissionId, AuditResult.SUCCESS, message);
    }
  }

  private EntryView toEntryView(PermissionEntry entry, Map<UUID, Asset> assetById) {
    Asset asset = assetById.get(entry.getAssetId());
    return new EntryView(entry.getAssetId(), asset != null ? asset.getName() : "(삭제된 자산)", entry.getAction());
  }

  private UserAssignView toUserAssignView(PermissionUserAssignment assignment, Map<UUID, ManagedUser> userById) {
    ManagedUser user = userById.get(assignment.getUserId());
    return new UserAssignView(
        assignment.getUserId(),
        user != null ? user.getName() : "(알 수 없음)",
        user != null ? user.getUsername() : "",
        assignment.getExpiresAt());
  }

  private String groupName(Map<UUID, AccessGroup> groupById, UUID groupId) {
    AccessGroup group = groupById.get(groupId);
    return group != null ? group.getName() : "(삭제된 그룹)";
  }

  public record EntryView(UUID assetId, String assetName, PermissionProtocol action) {}

  public record GroupAssignView(UUID groupId, String groupName) {}

  public record UserAssignView(UUID userId, String name, String username, OffsetDateTime expiresAt) {}
}
