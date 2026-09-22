package com.moara.moa.maintenance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceWindowRepository extends JpaRepository<MaintenanceWindow, UUID> {
  List<MaintenanceWindow> findAllByTenantIdOrderByStartsAtDesc(UUID tenantId);

  List<MaintenanceWindow> findAllByTenantIdAndTargetTypeAndTargetIdOrderByStartsAtDesc(
      UUID tenantId, MaintenanceTargetType targetType, UUID targetId);
}
