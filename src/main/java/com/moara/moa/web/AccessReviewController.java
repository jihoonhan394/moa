package com.moara.moa.web;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.group.AccessGroup;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.permission.Permission;
import com.moara.moa.permission.PermissionSetService;
import com.moara.moa.security.TenantContext;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 기관 관리자(TENANT_ADMIN)용 <b>읽기 전용</b> 접근 현황(거버넌스 오버사이트). 자원의 등록·부여는
 * 인프라 관리자(INFRA_MANAGER) 몫이지만, "누가 자원을 관리하고 누가 어느 자원에 접근권을 보유하는지"는
 * 기관 관리자가 감사할 수 있어야 한다(결정 5). 이 화면에는 어떤 변경 폼도 없다.
 * 접근 제어는 SecurityConfig(/access-review → TENANT_ADMIN)가 강제하며 현재 기관으로 격리된다.
 */
@Controller
public class AccessReviewController {
  private final PermissionSetService permissionService;
  private final AssetService assetService;
  private final AccessGroupService groupService;
  private final ManagedUserService userService;
  private final TenantContext tenantContext;

  public AccessReviewController(
      PermissionSetService permissionService,
      AssetService assetService,
      AccessGroupService groupService,
      ManagedUserService userService,
      TenantContext tenantContext) {
    this.permissionService = permissionService;
    this.assetService = assetService;
    this.groupService = groupService;
    this.userService = userService;
    this.tenantContext = tenantContext;
  }

  @GetMapping("/access-review")
  public String review(Model model) {
    UUID tenantId = tenantContext.currentTenantId();

    List<ManagedUser> users = userService.findByTenant(tenantId);
    Map<UUID, ManagedUser> userById = users.stream()
        .collect(Collectors.toMap(ManagedUser::getId, u -> u));
    Map<UUID, String> groupById = groupService.findAll(tenantId).stream()
        .collect(Collectors.toMap(AccessGroup::getId, AccessGroup::getName));

    List<ManagedUser> infraManagers = users.stream()
        .filter(u -> u.hasRole(UserRole.INFRA_MANAGER))
        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
        .toList();

    List<PermissionRow> rows = permissionService.findAll(tenantId).stream()
        .map(permission -> toRow(tenantId, permission, groupById, userById))
        .toList();

    model.addAttribute("infraManagers", infraManagers);
    model.addAttribute("permissionRows", rows);
    model.addAttribute("assets", assetService.findAll(tenantId));
    return "access-review";
  }

  private PermissionRow toRow(
      UUID tenantId, Permission permission,
      Map<UUID, String> groupById, Map<UUID, ManagedUser> userById) {
    List<String> groups = permissionService.findGroupAssignments(tenantId, permission.getId()).stream()
        .map(a -> groupById.getOrDefault(a.getGroupId(), "(삭제된 그룹)"))
        .toList();
    List<UserGrant> userGrants = permissionService.findUserAssignments(tenantId, permission.getId()).stream()
        .map(a -> {
          ManagedUser user = userById.get(a.getUserId());
          String label = user != null ? user.getName() + " (" + user.getUsername() + ")" : "(알 수 없음)";
          return new UserGrant(label, a.getExpiresAt());
        })
        .toList();
    return new PermissionRow(permission.getName(), permission.getStatus().name(), groups, userGrants);
  }

  public record UserGrant(String label, OffsetDateTime expiresAt) {}

  public record PermissionRow(String name, String status, List<String> groups, List<UserGrant> users) {}
}
