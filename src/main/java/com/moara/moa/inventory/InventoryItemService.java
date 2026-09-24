package com.moara.moa.inventory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실물·SW 인벤토리 관리(자산 관리자). 모든 조회·변경은 현재 기관으로 스코프된다. 등록·수정은 메타만,
 * 배정/회수/폐기는 별도 상태 전이로 다룬다. 퇴사 회수를 위해 사용자별 배정 조회·일괄 회수도 제공한다.
 */
@Service
@Transactional(readOnly = true)
public class InventoryItemService {
  private final InventoryItemRepository repository;

  private final InventoryCustodyService custodyService;

  public InventoryItemService(
      InventoryItemRepository repository, InventoryCustodyService custodyService) {
    this.repository = repository;
    this.custodyService = custodyService;
  }

  public List<InventoryItem> findAll(UUID tenantId) {
    return repository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public InventoryItem findById(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new InventoryItemNotFoundException(id));
  }

  @Transactional
  public InventoryItem create(UUID tenantId, InventoryItemForm form) {
    requireUniqueName(tenantId, form.name(), null);
    return repository.save(new InventoryItem(UUID.randomUUID(), tenantId, form, OffsetDateTime.now()));
  }

  @Transactional
  public InventoryItem update(UUID tenantId, UUID id, InventoryItemForm form) {
    InventoryItem item = findById(tenantId, id);
    requireUniqueName(tenantId, form.name(), id);
    item.applyMeta(form, OffsetDateTime.now());
    return repository.save(item);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    repository.delete(findById(tenantId, id));
  }

  @Transactional
  public InventoryItem assign(UUID tenantId, UUID id, UUID userId) {
    return assign(tenantId, id, userId, null);
  }

  /**
   * 사용자에게 배정. 상태 변경과 함께 <b>보관 장부에 구간을 남긴다</b> — 그래야 "이 장비가
   * 지금까지 누구 손을 거쳤나"에 답할 수 있다. {@code assignedUserId}는 활성 구간의 캐시다.
   */
  @Transactional
  public InventoryItem assign(UUID tenantId, UUID id, UUID userId, UUID actorId) {
    InventoryItem item = findById(tenantId, id);
    item.assignTo(userId, OffsetDateTime.now());
    InventoryItem saved = repository.save(item);
    custodyService.toUser(tenantId, id, userId, "배정", actorId);
    return saved;
  }

  @Transactional
  public InventoryItem reclaim(UUID tenantId, UUID id) {
    return reclaim(tenantId, id, null, "회수");
  }

  @Transactional
  public InventoryItem reclaim(UUID tenantId, UUID id, UUID actorId, String reason) {
    InventoryItem item = findById(tenantId, id);
    item.reclaim(OffsetDateTime.now());
    InventoryItem saved = repository.save(item);
    custodyService.toWarehouse(tenantId, id, reason, actorId);
    return saved;
  }

  @Transactional
  public InventoryItem retire(UUID tenantId, UUID id) {
    return retire(tenantId, id, null);
  }

  @Transactional
  public InventoryItem retire(UUID tenantId, UUID id, UUID actorId) {
    InventoryItem item = findById(tenantId, id);
    item.retire(OffsetDateTime.now());
    InventoryItem saved = repository.save(item);
    custodyService.toDisposed(tenantId, id, "폐기", actorId);
    return saved;
  }

  /** 특정 사용자에게 배정된 항목. */
  public List<InventoryItem> findAssignedTo(UUID tenantId, UUID userId) {
    return repository.findAllByTenantIdAndAssignedUserId(tenantId, userId);
  }

  /** 소유팀(그룹) 집합이 소유한 항목. 그룹이 없으면 빈 목록. */
  public List<InventoryItem> findOwnedByGroups(UUID tenantId, java.util.Collection<UUID> groupIds) {
    if (groupIds == null || groupIds.isEmpty()) {
      return List.of();
    }
    return repository.findAllByTenantIdAndOwnerGroupIdInOrderByNameAsc(tenantId, groupIds);
  }

  /** 소유팀(그룹) 배정/해제(자산 관리자). groupId=null이면 자산관리자 전용으로 되돌린다. */
  @Transactional
  public InventoryItem assignOwnerGroup(UUID tenantId, UUID id, UUID groupId) {
    InventoryItem item = findById(tenantId, id);
    item.assignOwnerGroup(groupId, OffsetDateTime.now());
    return repository.save(item);
  }

  /** 퇴사 회수: 사용자에게 배정된 모든 항목을 회수(AVAILABLE). 회수 건수 반환. */
  @Transactional
  public long reclaimAllFrom(UUID tenantId, UUID userId) {
    List<InventoryItem> assigned = repository.findAllByTenantIdAndAssignedUserId(tenantId, userId);
    OffsetDateTime now = OffsetDateTime.now();
    for (InventoryItem item : assigned) {
      item.reclaim(now);
    }
    repository.saveAll(assigned);
    // 퇴사 회수도 장부에 남긴다. 행위자는 이 계층에서 알 수 없어(OffboardHandler SPI에 없다)
    // null이며, 사유가 그 맥락을 대신한다 — "누가 퇴사 처리했나"는 감사 로그가 답한다.
    for (InventoryItem item : assigned) {
      custodyService.toWarehouse(tenantId, item.getId(), "퇴사 회수", null);
    }
    return assigned.size();
  }

  private void requireUniqueName(UUID tenantId, String name, UUID excludeId) {
    repository.findByTenantIdAndName(tenantId, name).ifPresent(existing -> {
      if (!existing.getId().equals(excludeId)) {
        throw new DuplicateInventoryItemException(name);
      }
    });
  }
}
