package com.moara.moa.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
  List<InventoryItem> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<InventoryItem> findByTenantIdAndId(UUID tenantId, UUID id);

  /**
   * 이름이 같은 항목들. V66부터 <b>여러 건일 수 있다</b> — 부품은 같은 이름이 여러 장비에
   * 들어가는 것이 정상이라 DB 유니크를 걷어냈다(중복 판정은 서비스가 상위 자산에만 적용).
   */
  List<InventoryItem> findByTenantIdAndName(UUID tenantId, String name);

  /** 특정 사용자에게 배정된 항목(퇴사 회수 대상). */
  List<InventoryItem> findAllByTenantIdAndAssignedUserId(UUID tenantId, UUID assignedUserId);

  /** 장비에 장착된 구성품. */
  List<InventoryItem> findAllByTenantIdAndParentItemId(UUID tenantId, UUID parentItemId);

  /** 아직 어디에도 장착되지 않은 자산(장착 후보). */
  List<InventoryItem> findAllByTenantIdAndParentItemIdIsNull(UUID tenantId);

  /** 소유팀(그룹) 집합이 소유한 항목(팀 자산 조회). */
  List<InventoryItem> findAllByTenantIdAndOwnerGroupIdInOrderByNameAsc(
      UUID tenantId, java.util.Collection<UUID> ownerGroupIds);
}
