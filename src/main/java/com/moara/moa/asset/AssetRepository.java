package com.moara.moa.asset;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
  // 모든 조회는 tenant_id로 스코프한다(테넌트 격리). 전역 조회 메서드는 두지 않는다.
  List<Asset> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  List<Asset> findAllByTenantIdAndAssetTypeOrderByNameAsc(UUID tenantId, AssetType assetType);

  /** 소유팀(그룹) 집합이 소유한 자산(팀 소유 서버 조회). */
  List<Asset> findAllByTenantIdAndOwnerGroupIdIn(UUID tenantId, java.util.Collection<UUID> ownerGroupIds);

  List<Asset> findTop5ByTenantIdOrderByUpdatedAtDesc(UUID tenantId);

  Optional<Asset> findByIdAndTenantId(UUID id, UUID tenantId);

  long countByTenantId(UUID tenantId);

  long countByTenantIdAndAssetType(UUID tenantId, AssetType assetType);

  long countByTenantIdAndStatus(UUID tenantId, AssetStatus status);

  // 사용자가 접근 가능한 활성 자산(묶음 권한 모델, T18). 접근 경로:
  //   (권한→엔트리) ∧ ( 소속 활성그룹에 부착 ∨ 만료 안 된 개인 부착 ). 활성 권한/그룹/자산만 포함.
  @Query(value = """
      SELECT DISTINCT a.* FROM assets a
      JOIN permission_entries pe ON pe.asset_id = a.id AND pe.tenant_id = a.tenant_id
      JOIN permissions p ON p.id = pe.permission_id AND p.tenant_id = pe.tenant_id AND p.status = 'ACTIVE'
      WHERE a.tenant_id = :tenantId
        AND a.status = 'ACTIVE'
        AND (
          EXISTS (
            SELECT 1 FROM permission_group_assignments pga
            JOIN access_groups g ON g.id = pga.group_id AND g.tenant_id = pga.tenant_id AND g.status = 'ACTIVE'
            JOIN user_group_members ugm ON ugm.group_id = pga.group_id AND ugm.tenant_id = pga.tenant_id
            WHERE pga.permission_id = p.id AND pga.tenant_id = :tenantId AND ugm.user_id = :userId
          )
          OR EXISTS (
            SELECT 1 FROM permission_user_assignments pua
            WHERE pua.permission_id = p.id AND pua.tenant_id = :tenantId AND pua.user_id = :userId
              AND (pua.expires_at IS NULL OR pua.expires_at > :now)
          )
        )
      ORDER BY a.name ASC
      """, nativeQuery = true)
  List<Asset> findAccessibleAssets(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("now") OffsetDateTime now);

  // 특정 자산 접근 가능 여부(>0이면 가능). 접속 전 권한 검사(T10)에서 재사용. 묶음 권한 모델 기준.
  @Query(value = """
      SELECT COUNT(*) FROM permission_entries pe
      JOIN permissions p ON p.id = pe.permission_id AND p.tenant_id = pe.tenant_id AND p.status = 'ACTIVE'
      JOIN assets a ON a.id = pe.asset_id AND a.tenant_id = pe.tenant_id AND a.status = 'ACTIVE'
      WHERE pe.tenant_id = :tenantId
        AND pe.asset_id = :assetId
        AND (
          EXISTS (
            SELECT 1 FROM permission_group_assignments pga
            JOIN access_groups g ON g.id = pga.group_id AND g.tenant_id = pga.tenant_id AND g.status = 'ACTIVE'
            JOIN user_group_members ugm ON ugm.group_id = pga.group_id AND ugm.tenant_id = pga.tenant_id
            WHERE pga.permission_id = p.id AND pga.tenant_id = :tenantId AND ugm.user_id = :userId
          )
          OR EXISTS (
            SELECT 1 FROM permission_user_assignments pua
            WHERE pua.permission_id = p.id AND pua.tenant_id = :tenantId AND pua.user_id = :userId
              AND (pua.expires_at IS NULL OR pua.expires_at > :now)
          )
        )
      """, nativeQuery = true)
  long countAccessPaths(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("assetId") UUID assetId,
      @Param("now") OffsetDateTime now);
}
