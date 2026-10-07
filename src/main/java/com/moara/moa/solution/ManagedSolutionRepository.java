package com.moara.moa.solution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedSolutionRepository extends JpaRepository<ManagedSolution, UUID> {
  List<ManagedSolution> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  List<ManagedSolution> findAllByTenantIdAndAssetIdOrderByNameAsc(UUID tenantId, UUID assetId);

  Optional<ManagedSolution> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<ManagedSolution> findByTenantIdAndAssetIdAndName(UUID tenantId, UUID assetId, String name);

  /**
   * 솔루션 목록의 검색·필터. 거르기를 DB 가 한다.
   *
   * @param term 이름·식별자·분류에서 찾을 말. {@code null} 이면 전체
   * @param category 관리형 분류 경로. {@code null} 이면 전체
   * @param ownerGroupId 이 소유팀 것만. {@code null} 이면 전체
   */
  @org.springframework.data.jpa.repository.Query("""
      select s from ManagedSolution s
      where s.tenantId = :tenantId
        and (:category is null or s.category = :category)
        and (:ownerGroupId is null or s.ownerGroupId = :ownerGroupId)
        and (:term is null
             or lower(s.name) like lower(concat('%', :term, '%')) escape '!'
             or lower(s.identifier) like lower(concat('%', :term, '%')) escape '!'
             or lower(s.category) like lower(concat('%', :term, '%')) escape '!')
      order by s.name asc
      """)
  org.springframework.data.domain.Page<ManagedSolution> filter(
      @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
      @org.springframework.data.repository.query.Param("term") String term,
      @org.springframework.data.repository.query.Param("category") String category,
      @org.springframework.data.repository.query.Param("ownerGroupId") UUID ownerGroupId,
      org.springframework.data.domain.Pageable pageable);

  long countByTenantIdAndCredentialId(UUID tenantId, UUID credentialId);
}
