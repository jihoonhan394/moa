package com.moara.moa.inventory.tag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetTagRepository extends JpaRepository<AssetTag, String> {

  /** 코드는 전역 유니크다. 기관 검증은 서비스에서 한다(없는 코드와 남의 코드를 같게 다루려고). */
  Optional<AssetTag> findByCode(String code);

  Optional<AssetTag> findByTenantIdAndItemId(UUID tenantId, UUID itemId);

  List<AssetTag> findAllByTenantIdAndItemIdIsNullOrderByIssuedAtDesc(UUID tenantId);

  long countByTenantIdAndItemIdIsNull(UUID tenantId);

  boolean existsByCode(String code);

  /** 이미 번호가 붙은 자산들. 붙이기 화면에서 중복 부착을 막는 데 쓴다. */
  List<AssetTag> findAllByTenantIdAndItemIdIn(UUID tenantId, java.util.Collection<UUID> itemIds);
}
