package com.moara.moa.wiki;

import com.moara.moa.group.UserGroupMember;
import com.moara.moa.group.UserGroupMemberRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위키 공간 접근 판정. 권한은 공간 단위(전체/그룹/개인 × 열람/편집/관리)이며 상위 폴더에서 하위로 상속된다
 * (부모의 최고 수준이 자식에 적용). 권한이 하나도 없으면 접근 불가(fail-closed). 기관 관리자는 백업으로
 * 전 공간 MANAGE.
 */
@Service
@Transactional(readOnly = true)
public class WikiAccessService {
  private final WikiSpaceRepository spaceRepository;
  private final WikiSpacePermissionRepository permissionRepository;
  private final UserGroupMemberRepository groupMemberRepository;

  public WikiAccessService(
      WikiSpaceRepository spaceRepository, WikiSpacePermissionRepository permissionRepository,
      UserGroupMemberRepository groupMemberRepository) {
    this.spaceRepository = spaceRepository;
    this.permissionRepository = permissionRepository;
    this.groupMemberRepository = groupMemberRepository;
  }

  /** 한 공간에 대한 유효 접근 수준(상속 포함). null=접근 불가. 기관 관리자면 항상 MANAGE. */
  public WikiAccessLevel effectiveLevel(UUID tenantId, UUID userId, boolean tenantAdmin, UUID spaceId) {
    if (tenantAdmin) {
      return WikiAccessLevel.MANAGE;
    }
    return levelFor(spaceId, userGroupIds(tenantId, userId), leaderGroupIds(tenantId, userId), userId,
        spacesById(tenantId), permsBySpace(tenantId));
  }

  public boolean canView(UUID tenantId, UUID userId, boolean tenantAdmin, UUID spaceId) {
    return effectiveLevel(tenantId, userId, tenantAdmin, spaceId) != null;
  }

  public boolean canEdit(UUID tenantId, UUID userId, boolean tenantAdmin, UUID spaceId) {
    WikiAccessLevel level = effectiveLevel(tenantId, userId, tenantAdmin, spaceId);
    return level != null && level.atLeast(WikiAccessLevel.EDIT);
  }

  public boolean canManage(UUID tenantId, UUID userId, boolean tenantAdmin, UUID spaceId) {
    WikiAccessLevel level = effectiveLevel(tenantId, userId, tenantAdmin, spaceId);
    return level != null && level.atLeast(WikiAccessLevel.MANAGE);
  }

  /** 열람 이상 가능한 공간들(이름순). */
  public List<WikiSpace> accessibleSpaces(UUID tenantId, UUID userId, boolean tenantAdmin) {
    Map<UUID, WikiSpace> byId = spacesById(tenantId);
    Map<UUID, List<WikiSpacePermission>> perms = permsBySpace(tenantId);
    Set<UUID> groups = userGroupIds(tenantId, userId);
    Set<UUID> leaderGroups = leaderGroupIds(tenantId, userId);
    return spaceRepository.findAllByTenantIdOrderByNameAsc(tenantId).stream()
        .filter(space -> tenantAdmin || levelFor(space.getId(), groups, leaderGroups, userId, byId, perms) != null)
        .toList();
  }

  private WikiAccessLevel levelFor(
      UUID spaceId, Set<UUID> groups, Set<UUID> leaderGroups, UUID userId,
      Map<UUID, WikiSpace> byId, Map<UUID, List<WikiSpacePermission>> permsBySpace) {
    WikiAccessLevel best = null;
    UUID current = spaceId;
    Set<UUID> visited = new HashSet<>();
    while (current != null && visited.add(current)) {
      for (WikiSpacePermission perm : permsBySpace.getOrDefault(current, List.of())) {
        if (matches(perm, userId, groups)) {
          // 부서장은 자기 부서(그룹) 공간을 관리한다: 그 그룹 대상 권한은 MANAGE로 승격(계산형, 저장 없음).
          WikiAccessLevel level = perm.getSubjectType() == WikiSubjectType.GROUP
              && leaderGroups.contains(perm.getSubjectId())
              ? WikiAccessLevel.MANAGE : perm.getAccessLevel();
          if (best == null || level.atLeast(best)) {
            best = level;
          }
        }
      }
      WikiSpace space = byId.get(current);
      current = space == null ? null : space.getParentId();
    }
    return best;
  }

  /** 이 사용자가 부서장(leader)인 그룹들. 위키 공간 관리 승격의 기준. */
  private Set<UUID> leaderGroupIds(UUID tenantId, UUID userId) {
    if (userId == null) {
      return Set.of();
    }
    return groupMemberRepository.findAllByTenantIdAndUserIdAndLeaderTrue(tenantId, userId).stream()
        .map(UserGroupMember::getGroupId)
        .collect(Collectors.toSet());
  }

  private boolean matches(WikiSpacePermission perm, UUID userId, Set<UUID> groups) {
    return switch (perm.getSubjectType()) {
      case ALL -> true;
      case USER -> userId != null && userId.equals(perm.getSubjectId());
      case GROUP -> groups.contains(perm.getSubjectId());
    };
  }

  private Set<UUID> userGroupIds(UUID tenantId, UUID userId) {
    if (userId == null) {
      return Set.of();
    }
    return groupMemberRepository.findAllByTenantIdAndUserId(tenantId, userId).stream()
        .map(UserGroupMember::getGroupId)
        .collect(Collectors.toSet());
  }

  private Map<UUID, WikiSpace> spacesById(UUID tenantId) {
    Map<UUID, WikiSpace> map = new HashMap<>();
    for (WikiSpace space : spaceRepository.findAllByTenantIdOrderByNameAsc(tenantId)) {
      map.put(space.getId(), space);
    }
    return map;
  }

  private Map<UUID, List<WikiSpacePermission>> permsBySpace(UUID tenantId) {
    Map<UUID, List<WikiSpacePermission>> map = new HashMap<>();
    for (WikiSpacePermission perm : permissionRepository.findAllByTenantId(tenantId)) {
      map.computeIfAbsent(perm.getSpaceId(), k -> new java.util.ArrayList<>()).add(perm);
    }
    return map;
  }
}
