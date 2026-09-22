package com.moara.moa.tracker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceTrackerRepository extends JpaRepository<ResourceTracker, UUID> {
  List<ResourceTracker> findAllByTenantIdAndTargetTypeAndTargetIdOrderByDueOnAsc(
      UUID tenantId, TrackerTargetType targetType, UUID targetId);

  List<ResourceTracker> findAllByTenantId(UUID tenantId);

  Optional<ResourceTracker> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<ResourceTracker> findFirstByTenantIdAndTargetTypeAndTargetIdAndSource(
      UUID tenantId, TrackerTargetType targetType, UUID targetId, String source);

  /** 일 배치 재프로브 대상: source(SSL_PROBE 등)로 전 기관 항목을 모은다(스케줄러 전용). */
  List<ResourceTracker> findAllBySource(String source);
}
