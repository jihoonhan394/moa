package com.moara.moa.inventory;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 퇴사 시 자산 처리: 배정된 항목을 <b>반납 대기</b>로 표시한다.
 *
 * <p>여기서 실물이 돌아왔다고 기록하지 않는다 — 아무도 보지 않았기 때문이다. 자산 관리자가
 * 확인할 때까지 그 사람에게 열린 보관 구간이 유지된다.
 */
@Component
public class InventoryOffboardHandler implements OffboardHandler {
  private final InventoryItemService inventoryService;

  public InventoryOffboardHandler(InventoryItemService inventoryService) {
    this.inventoryService = inventoryService;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome(
        "자산 반납 요청(실물 확인 필요)", inventoryService.requestReturnFrom(tenantId, userId));
  }
}
