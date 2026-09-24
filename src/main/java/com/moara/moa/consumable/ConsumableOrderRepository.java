package com.moara.moa.consumable;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumableOrderRepository extends JpaRepository<ConsumableOrder, UUID> {
  List<ConsumableOrder> findByTenantIdAndItemIdOrderByOrderedOnDesc(UUID tenantId, UUID itemId);

  /** 기관의 주문 전체. 품목별 예측을 한 번에 계산할 때 메모리에서 나눈다(품목 수가 적다). */
  List<ConsumableOrder> findByTenantId(UUID tenantId);
}
