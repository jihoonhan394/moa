package com.moara.moa.permission;

import com.moara.moa.asset.AssetService;
import com.moara.moa.group.AccessGroupService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 묶음 권한(Permission Set) 관리: 권한 생성/수정/비활성, 엔트리(자산×액션) 추가/삭제,
 * 그룹 부착(주력)/사용자 부착(일시 예외, 만료). 부착·엔트리 시 대상이 같은 테넌트에 속하는지
 * 각 도메인 서비스의 {@code findById(tenantId, ...)}로 검증한다(타 테넌트는 NotFound = 격리).
 * 접근 판정(누가 무엇에 접근 가능한가)은 T18에서 이 모델을 조회해 구현한다.
 */
@Service
@Transactional(readOnly = true)
public class PermissionSetService {
  private final PermissionRepository permissionRepository;
  private final PermissionEntryRepository entryRepository;
  private final PermissionGroupAssignmentRepository groupAssignmentRepository;
  private final PermissionUserAssignmentRepository userAssignmentRepository;
  private final AccessGroupService accessGroupService;
  private final AssetService assetService;
  private final ManagedUserService userService;
  private final Clock clock;

  @Autowired
  public PermissionSetService(
      PermissionRepository permissionRepository,
      PermissionEntryRepository entryRepository,
      PermissionGroupAssignmentRepository groupAssignmentRepository,
      PermissionUserAssignmentRepository userAssignmentRepository,
      AccessGroupService accessGroupService,
      AssetService assetService,
      ManagedUserService userService) {
    this(permissionRepository, entryRepository, groupAssignmentRepository, userAssignmentRepository,
        accessGroupService, assetService, userService, Clock.systemUTC());
  }

  PermissionSetService(
      PermissionRepository permissionRepository,
      PermissionEntryRepository entryRepository,
      PermissionGroupAssignmentRepository groupAssignmentRepository,
      PermissionUserAssignmentRepository userAssignmentRepository,
      AccessGroupService accessGroupService,
      AssetService assetService,
      ManagedUserService userService,
      Clock clock) {
    this.permissionRepository = permissionRepository;
    this.entryRepository = entryRepository;
    this.groupAssignmentRepository = groupAssignmentRepository;
    this.userAssignmentRepository = userAssignmentRepository;
    this.accessGroupService = accessGroupService;
    this.assetService = assetService;
    this.userService = userService;
    this.clock = clock;
  }

  // ----- 권한(묶음) -----

  @Transactional
  public Permission create(UUID tenantId, PermissionForm form) {
    permissionRepository.findByTenantIdAndName(tenantId, form.name().trim()).ifPresent(existing -> {
      throw new DuplicatePermissionException(form.name());
    });
    return permissionRepository.save(new Permission(UUID.randomUUID(), tenantId, form, OffsetDateTime.now(clock)));
  }

  public Permission findById(UUID tenantId, UUID permissionId) {
    return permissionRepository.findByTenantIdAndId(tenantId, permissionId)
        .orElseThrow(() -> new PermissionNotFoundException(permissionId));
  }

  public List<Permission> findAll(UUID tenantId) {
    return permissionRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  @Transactional
  public Permission update(UUID tenantId, UUID permissionId, PermissionForm form) {
    Permission permission = findById(tenantId, permissionId);
    permissionRepository.findByTenantIdAndName(tenantId, form.name().trim())
        .filter(other -> !other.getId().equals(permissionId))
        .ifPresent(other -> {
          throw new DuplicatePermissionException(form.name());
        });
    permission.apply(form, OffsetDateTime.now(clock));
    return permissionRepository.save(permission);
  }

  @Transactional
  public void disable(UUID tenantId, UUID permissionId) {
    Permission permission = findById(tenantId, permissionId);
    permission.disable(OffsetDateTime.now(clock));
    permissionRepository.save(permission);
  }

  // ----- 엔트리(자산 × 액션) -----

  @Transactional
  public PermissionEntry addEntry(UUID tenantId, UUID permissionId, UUID assetId, PermissionProtocol action) {
    findById(tenantId, permissionId);          // 권한 소유권
    assetService.findById(tenantId, assetId);  // 자산 소유권(타 테넌트 NotFound)
    return entryRepository
        .findByTenantIdAndPermissionIdAndAssetIdAndAction(tenantId, permissionId, assetId, action)
        .orElseGet(() -> entryRepository.save(new PermissionEntry(
            UUID.randomUUID(), tenantId, permissionId, assetId, action, OffsetDateTime.now(clock))));
  }

  @Transactional
  public void removeEntry(UUID tenantId, UUID permissionId, UUID assetId, PermissionProtocol action) {
    findById(tenantId, permissionId);
    entryRepository
        .findByTenantIdAndPermissionIdAndAssetIdAndAction(tenantId, permissionId, assetId, action)
        .ifPresent(entryRepository::delete);
  }

  public List<PermissionEntry> findEntries(UUID tenantId, UUID permissionId) {
    findById(tenantId, permissionId);
    return entryRepository.findAllByTenantIdAndPermissionId(tenantId, permissionId);
  }

  // ----- 그룹 부착 -----

  @Transactional
  public PermissionGroupAssignment assignToGroup(UUID tenantId, UUID permissionId, UUID groupId) {
    findById(tenantId, permissionId);
    accessGroupService.findById(tenantId, groupId); // 그룹 소유권
    return groupAssignmentRepository
        .findByTenantIdAndPermissionIdAndGroupId(tenantId, permissionId, groupId)
        .orElseGet(() -> groupAssignmentRepository.save(new PermissionGroupAssignment(
            UUID.randomUUID(), tenantId, permissionId, groupId, OffsetDateTime.now(clock))));
  }

  @Transactional
  public void unassignGroup(UUID tenantId, UUID permissionId, UUID groupId) {
    findById(tenantId, permissionId);
    groupAssignmentRepository
        .findByTenantIdAndPermissionIdAndGroupId(tenantId, permissionId, groupId)
        .ifPresent(groupAssignmentRepository::delete);
  }

  public List<PermissionGroupAssignment> findGroupAssignments(UUID tenantId, UUID permissionId) {
    findById(tenantId, permissionId);
    return groupAssignmentRepository.findAllByTenantIdAndPermissionId(tenantId, permissionId);
  }

  // ----- 사용자 부착(일시 예외) -----

  /** 사용자에 권한을 부착한다. 이미 있으면 만료 시각만 갱신한다(멱등). expiresAt null=무기한. */
  @Transactional
  public PermissionUserAssignment assignToUser(
      UUID tenantId, UUID permissionId, UUID userId, OffsetDateTime expiresAt) {
    findById(tenantId, permissionId);
    requireSameTenantUser(tenantId, userId);
    return userAssignmentRepository
        .findByTenantIdAndPermissionIdAndUserId(tenantId, permissionId, userId)
        .map(existing -> {
          existing.updateExpiry(expiresAt);
          return userAssignmentRepository.save(existing);
        })
        .orElseGet(() -> userAssignmentRepository.save(new PermissionUserAssignment(
            UUID.randomUUID(), tenantId, permissionId, userId, expiresAt, OffsetDateTime.now(clock))));
  }

  @Transactional
  public void unassignUser(UUID tenantId, UUID permissionId, UUID userId) {
    findById(tenantId, permissionId);
    userAssignmentRepository
        .findByTenantIdAndPermissionIdAndUserId(tenantId, permissionId, userId)
        .ifPresent(userAssignmentRepository::delete);
  }

  public List<PermissionUserAssignment> findUserAssignments(UUID tenantId, UUID permissionId) {
    findById(tenantId, permissionId);
    return userAssignmentRepository.findAllByTenantIdAndPermissionId(tenantId, permissionId);
  }

  private void requireSameTenantUser(UUID tenantId, UUID userId) {
    ManagedUser user = userService.findById(userId);
    if (user.getTenantId() == null || !user.getTenantId().equals(tenantId)) {
      throw new CrossTenantPermissionAssignmentException(userId, tenantId);
    }
  }
}
