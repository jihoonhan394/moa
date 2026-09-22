package com.moara.moa.inventory;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자에게 배정된 인벤토리 항목 일괄 회수. */
@Component
public class InventoryOffboardHandler implements OffboardHandler {
  private final InventoryItemService inventoryService;

  public InventoryOffboardHandler(InventoryItemService inventoryService) {
    this.inventoryService = inventoryService;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("배정 자산", inventoryService.reclaimAllFrom(tenantId, userId));
  }
}
