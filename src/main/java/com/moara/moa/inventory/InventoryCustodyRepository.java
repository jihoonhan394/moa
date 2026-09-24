package com.moara.moa.inventory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryCustodyRepository extends JpaRepository<InventoryCustody, UUID> {

  /** 품목의 보관 이력(최근 구간이 위). */
  List<InventoryCustody> findByTenantIdAndItemIdOrderByStartedOnDescCreatedAtDesc(
      UUID tenantId, UUID itemId);

  /** 품목의 현재 열린 구간. 2c(부분 이동) 전까지는 품목당 하나다. */
  Optional<InventoryCustody> findFirstByTenantIdAndItemIdAndEndedOnIsNull(UUID tenantId, UUID itemId);

  /** 기관의 열린 구간 전체. 반납 초과 스캔·현황 집계에 쓴다. */
  List<InventoryCustody> findByTenantIdAndEndedOnIsNull(UUID tenantId);

  /** 지금까지 쓴 외부 보관처 이름(중복 제거). 입력 자동완성용. */
  @Query("select distinct c.holderName from InventoryCustody c "
      + "where c.tenantId = :tenantId and c.holderName is not null")
  List<String> findDistinctHolderNames(@Param("tenantId") UUID tenantId);
}
