package com.moara.moa.solution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionSequenceStepRepository extends JpaRepository<SolutionSequenceStep, UUID> {
  List<SolutionSequenceStep> findAllByTenantIdAndSequenceIdOrderByPositionAsc(UUID tenantId, UUID sequenceId);

  Optional<SolutionSequenceStep> findByTenantIdAndId(UUID tenantId, UUID id);
}
