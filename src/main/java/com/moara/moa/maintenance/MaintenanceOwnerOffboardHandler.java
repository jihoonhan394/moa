package com.moara.moa.maintenance;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자를 모든 유지보수 담당(자산/솔루션)에서 해제. */
@Component
public class MaintenanceOwnerOffboardHandler implements OffboardHandler {
  private final MaintenanceService maintenanceService;

  public MaintenanceOwnerOffboardHandler(MaintenanceService maintenanceService) {
    this.maintenanceService = maintenanceService;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("유지보수 담당", maintenanceService.removeOwnershipsOf(tenantId, userId));
  }
}
