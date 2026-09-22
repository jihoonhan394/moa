package com.moara.moa.solution;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionUserAssignmentRepository
    extends JpaRepository<SolutionUserAssignment, UUID> {

  List<SolutionUserAssignment> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);

  List<SolutionUserAssignment> findAllByTenantIdAndSolutionId(UUID tenantId, UUID solutionId);

  boolean existsByTenantIdAndSolutionIdAndUserId(UUID tenantId, UUID solutionId, UUID userId);

  void deleteByTenantIdAndSolutionIdAndUserId(UUID tenantId, UUID solutionId, UUID userId);

  /** 퇴사 회수: 이 사용자의 모든 솔루션 배정 삭제. 삭제 건수 반환. */
  long deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
