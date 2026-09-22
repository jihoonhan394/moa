package com.moara.moa.permission;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetRepository;
import com.moara.moa.deputy.DeputyService;
import com.moara.moa.group.UserGroupMember;
import com.moara.moa.group.UserGroupMemberRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 사용자가 접근 가능한 자산을 조회한다(묶음 권한 모델, T18).
 * 접근 경로: (권한→엔트리) ∧ (소속 활성그룹 부착 ∨ 만료 안 된 개인 부착). 활성 권한/그룹/자산만.
 * <p>추가로 <b>소유팀(그룹) 부착</b>과 <b>대직(임시 부재)</b>을 가산 경로로 합친다: 자산의 소유팀에 속하거나,
 * 대직 중인 부재자가 접근 가능하면 접근 가능하다. 민감한 권한 SQL은 건드리지 않고 Java에서 합집합.
 */
@Service
@Transactional(readOnly = true)
public class AccessibleAssetService {
  private final AssetRepository assetRepository;
  private final UserGroupMemberRepository groupMemberRepository;
  private final DeputyService deputyService;

  public AccessibleAssetService(
      AssetRepository assetRepository, UserGroupMemberRepository groupMemberRepository,
      DeputyService deputyService) {
    this.assetRepository = assetRepository;
    this.groupMemberRepository = groupMemberRepository;
    this.deputyService = deputyService;
  }

  /** 유효 사용자 집합: 본인 + 대직 중인 부재자. */
  private Set<UUID> effectiveUsers(UUID tenantId, UUID userId) {
    Set<UUID> users = new HashSet<>();
    users.add(userId);
    users.addAll(deputyService.coveredAbsentUsers(tenantId, userId));
    return users;
  }

  /** 사용자가 접근 가능한 자산 목록(권한 ∪ 소유팀 ∪ 대직 경로, 이름순, 중복 제거). */
  public List<Asset> findAccessibleAssets(UUID tenantId, UUID userId) {
    Map<UUID, Asset> byId = new LinkedHashMap<>();
    Set<UUID> allGroups = new HashSet<>();
    for (UUID uid : effectiveUsers(tenantId, userId)) {
      for (Asset a : assetRepository.findAccessibleAssets(tenantId, uid, OffsetDateTime.now())) {
        byId.put(a.getId(), a);
      }
      allGroups.addAll(groupIdsOf(tenantId, uid));
    }
    if (!allGroups.isEmpty()) {
      for (Asset a : assetRepository.findAllByTenantIdAndOwnerGroupIdIn(tenantId, allGroups)) {
        byId.putIfAbsent(a.getId(), a);
      }
    }
    return byId.values().stream()
        .sorted((x, y) -> x.getName().compareToIgnoreCase(y.getName()))
        .toList();
  }

  /** 사용자가 특정 자산에 접근 가능한지: 권한 · 소유팀 · 대직 경로. 접속 시도 전 검사(T10)에서 재사용. */
  public boolean canAccess(UUID tenantId, UUID userId, UUID assetId) {
    Set<UUID> users = effectiveUsers(tenantId, userId);
    for (UUID uid : users) {
      if (assetRepository.countAccessPaths(tenantId, uid, assetId, OffsetDateTime.now()) > 0) {
        return true;
      }
    }
    UUID ownerGroupId = assetRepository.findByIdAndTenantId(assetId, tenantId)
        .map(Asset::getOwnerGroupId).orElse(null);
    if (ownerGroupId == null) {
      return false;
    }
    return users.stream().anyMatch(uid -> groupIdsOf(tenantId, uid).contains(ownerGroupId));
  }

  private Set<UUID> groupIdsOf(UUID tenantId, UUID userId) {
    return groupMemberRepository.findAllByTenantIdAndUserId(tenantId, userId).stream()
        .map(UserGroupMember::getGroupId)
        .collect(Collectors.toSet());
  }
}
