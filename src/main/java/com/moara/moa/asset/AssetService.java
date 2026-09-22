package com.moara.moa.asset;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@Transactional(readOnly = true)
public class AssetService {
  private final AssetRepository assetRepository;
  private final Clock clock;

  @Autowired
  public AssetService(AssetRepository assetRepository) {
    this(assetRepository, Clock.systemUTC());
  }

  AssetService(AssetRepository assetRepository, Clock clock) {
    this.assetRepository = assetRepository;
    this.clock = clock;
  }

  public List<Asset> findAll(UUID tenantId) {
    return assetRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public List<Asset> findAllByType(UUID tenantId, AssetType assetType) {
    return assetRepository.findAllByTenantIdAndAssetTypeOrderByNameAsc(tenantId, assetType);
  }

  public List<Asset> findRecentAssets(UUID tenantId) {
    return assetRepository.findTop5ByTenantIdOrderByUpdatedAtDesc(tenantId);
  }

  public long countAssets(UUID tenantId) {
    return assetRepository.countByTenantId(tenantId);
  }

  /** 전 기관 자산 총계(플랫폼 콘솔 집계용). */
  public long countAll() {
    return assetRepository.count();
  }

  public long countAssetsByType(UUID tenantId, AssetType assetType) {
    return assetRepository.countByTenantIdAndAssetType(tenantId, assetType);
  }

  public long countAssetsByStatus(UUID tenantId, AssetStatus status) {
    return assetRepository.countByTenantIdAndStatus(tenantId, status);
  }

  /**
   * 테넌트 소유권을 강제한다. 다른 테넌트의 자산 id를 조회하면 존재를 노출하지 않고
   * {@link AssetNotFoundException}으로 처리한다(격리·정보 비노출).
   */
  public Asset findById(UUID tenantId, UUID id) {
    return assetRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new AssetNotFoundException(id));
  }

  @Transactional
  public Asset create(UUID tenantId, AssetForm form) {
    return assetRepository.save(new Asset(UUID.randomUUID(), tenantId, form, OffsetDateTime.now(clock)));
  }

  @Transactional
  public Asset update(UUID tenantId, UUID id, AssetForm form) {
    Asset asset = findById(tenantId, id);
    asset.update(form, OffsetDateTime.now(clock));
    return asset;
  }

  /** 소유팀(그룹) 배정/해제. groupId=null이면 소유팀 해제(기존 권한 모델만). */
  @Transactional
  public Asset assignOwnerGroup(UUID tenantId, UUID id, UUID groupId) {
    Asset asset = findById(tenantId, id);
    asset.assignOwnerGroup(groupId, OffsetDateTime.now(clock));
    return asset;
  }

  /** 소유팀(그룹) 집합이 소유한 자산. 그룹이 없으면 빈 목록. */
  public List<Asset> findOwnedByGroups(UUID tenantId, java.util.Collection<UUID> groupIds) {
    if (groupIds == null || groupIds.isEmpty()) {
      return List.of();
    }
    return assetRepository.findAllByTenantIdAndOwnerGroupIdIn(tenantId, groupIds);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    assetRepository.delete(findById(tenantId, id));
  }
}
