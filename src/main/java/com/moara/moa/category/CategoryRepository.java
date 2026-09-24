package com.moara.moa.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
  List<Category> findByTenantIdAndDomainOrderBySortOrderAscNameAsc(UUID tenantId, CategoryDomain domain);

  long countByTenantIdAndDomain(UUID tenantId, CategoryDomain domain);

  boolean existsByTenantIdAndDomainAndNameIgnoreCase(UUID tenantId, CategoryDomain domain, String name);

  boolean existsByTenantIdAndDomainAndNameIgnoreCaseAndIdNot(
      UUID tenantId, CategoryDomain domain, String name, UUID id);

  Optional<Category> findByIdAndTenantId(UUID id, UUID tenantId);

  /** 같은 부모(형제) 중 동일 이름 존재? — 형제 범위 유일성 검사. */
  boolean existsByTenantIdAndDomainAndParentIdAndNameIgnoreCase(
      UUID tenantId, CategoryDomain domain, UUID parentId, String name);

  boolean existsByTenantIdAndDomainAndParentIdAndNameIgnoreCaseAndIdNot(
      UUID tenantId, CategoryDomain domain, UUID parentId, String name, UUID id);

  /** 자식 존재 확인은 기관 스코프로 한다(부모 id가 검증된 값이어도 조회를 전역으로 열지 않는다). */
  boolean existsByTenantIdAndParentId(UUID tenantId, UUID parentId);
}
