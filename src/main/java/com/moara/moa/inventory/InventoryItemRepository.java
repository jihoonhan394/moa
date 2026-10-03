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

  /**
   * 이름 또는 시리얼로 찾는다. 현장에서 관리번호를 기존 자산에 붙일 때 쓴다.
   *
   * <p>전체를 가져와 메모리에서 거르지 않는다 — 자산이 수천 건인 기관에서 그 방식은 매 요청마다
   * 테이블을 통째로 읽는다.
   */
  List<InventoryItem> findTop30ByTenantIdAndNameContainingIgnoreCaseOrderByNameAsc(
      UUID tenantId, String name);

  List<InventoryItem> findTop30ByTenantIdAndSerialNoContainingIgnoreCaseOrderByNameAsc(
      UUID tenantId, String serialNo);

  /**
   * <b>아직 관리번호가 붙지 않은</b> 자산만 찾는다.
   *
   * <p>⚠️ 이름으로 30건을 먼저 자른 뒤 거르면 안 된다. 앞쪽 30건이 전부 번호가 붙은 자산이면
   * 붙일 수 있는 자산이 한 건도 안 보인다 — 실제 데이터(자산 120건 중 70건에 번호)로 돌려 보니
   * 첫 화면이 통째로 "이미 번호가 붙어 있습니다"였다. 거른 다음에 잘라야 한다.
   */
  @org.springframework.data.jpa.repository.Query("""
      select i from InventoryItem i
      where i.tenantId = :tenantId
        and (lower(i.name) like lower(concat('%', :term, '%'))
             or lower(i.serialNo) like lower(concat('%', :term, '%')))
        and not exists (
          select t from AssetTag t where t.itemId = i.id
        )
      order by i.name asc
      """)
  List<InventoryItem> searchAttachable(
      @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
      @org.springframework.data.repository.query.Param("term") String term,
      org.springframework.data.domain.Pageable pageable);

  /** 반납 대기(퇴사했지만 실물 미확인) 목록. 관리자 화면의 미반납 배지가 쓴다. */
  List<InventoryItem> findAllByTenantIdAndStatus(UUID tenantId, InventoryItemStatus status);

  /** 장비에 장착된 구성품. */
  List<InventoryItem> findAllByTenantIdAndParentItemId(UUID tenantId, UUID parentItemId);

  /** 아직 어디에도 장착되지 않은 자산(장착 후보). */
  List<InventoryItem> findAllByTenantIdAndParentItemIdIsNull(UUID tenantId);

  /** 소유팀(그룹) 집합이 소유한 항목(팀 자산 조회). */
  List<InventoryItem> findAllByTenantIdAndOwnerGroupIdInOrderByNameAsc(
      UUID tenantId, java.util.Collection<UUID> ownerGroupIds);
}
