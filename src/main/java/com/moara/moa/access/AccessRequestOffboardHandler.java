package com.moara.moa.access;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 임시 접근(JIT) 승인은 회수하고 대기 중인 요청은 취소한다. */
@Component
public class AccessRequestOffboardHandler implements OffboardHandler {
  private final AccessApprovalService approvalService;

  public AccessRequestOffboardHandler(AccessApprovalService approvalService) {
    this.approvalService = approvalService;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("임시 접근(JIT)", approvalService.withdrawAllOf(tenantId, userId));
  }
}
