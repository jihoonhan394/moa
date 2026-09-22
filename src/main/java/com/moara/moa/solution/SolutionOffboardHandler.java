package com.moara.moa.solution;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자의 모든 솔루션 제어 배정 삭제. */
@Component
public class SolutionOffboardHandler implements OffboardHandler {
  private final SolutionUserAssignmentRepository repository;

  public SolutionOffboardHandler(SolutionUserAssignmentRepository repository) {
    this.repository = repository;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("솔루션 배정", repository.deleteByTenantIdAndUserId(tenantId, userId));
  }
}
