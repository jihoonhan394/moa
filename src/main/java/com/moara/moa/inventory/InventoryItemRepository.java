package com.moara.moa.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
  List<InventoryItem> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<InventoryItem> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<InventoryItem> findByTenantIdAndName(UUID tenantId, String name);

  /** 특정 사용자에게 배정된 항목(퇴사 회수 대상). */
  List<InventoryItem> findAllByTenantIdAndAssignedUserId(UUID tenantId, UUID assignedUserId);

  /** 소유팀(그룹) 집합이 소유한 항목(팀 자산 조회). */
  List<InventoryItem> findAllByTenantIdAndOwnerGroupIdInOrderByNameAsc(
      UUID tenantId, java.util.Collection<UUID> ownerGroupIds);
}
