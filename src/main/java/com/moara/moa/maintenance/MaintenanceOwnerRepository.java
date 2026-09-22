package com.moara.moa.maintenance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceOwnerRepository extends JpaRepository<MaintenanceOwner, UUID> {
  List<MaintenanceOwner> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  List<MaintenanceOwner> findAllByTenantIdAndTargetTypeAndTargetId(
      UUID tenantId, MaintenanceTargetType targetType, UUID targetId);

  Optional<MaintenanceOwner> findByTenantIdAndId(UUID tenantId, UUID id);

  boolean existsByTenantIdAndTargetTypeAndTargetIdAndUserId(
      UUID tenantId, MaintenanceTargetType targetType, UUID targetId, UUID userId);

  /** 퇴사 회수: 이 사용자를 담당자에서 제거. 삭제 건수 반환. */
  long deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
