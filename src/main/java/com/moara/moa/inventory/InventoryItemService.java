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

  public InventoryItemService(InventoryItemRepository repository) {
    this.repository = repository;
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
    InventoryItem item = findById(tenantId, id);
    item.assignTo(userId, OffsetDateTime.now());
    return repository.save(item);
  }

  @Transactional
  public InventoryItem reclaim(UUID tenantId, UUID id) {
    InventoryItem item = findById(tenantId, id);
    item.reclaim(OffsetDateTime.now());
    return repository.save(item);
  }

  @Transactional
  public InventoryItem retire(UUID tenantId, UUID id) {
    InventoryItem item = findById(tenantId, id);
    item.retire(OffsetDateTime.now());
    return repository.save(item);
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
