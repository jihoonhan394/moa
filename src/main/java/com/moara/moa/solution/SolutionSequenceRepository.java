package com.moara.moa.solution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionSequenceRepository extends JpaRepository<SolutionSequence, UUID> {
  List<SolutionSequence> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<SolutionSequence> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<SolutionSequence> findByTenantIdAndName(UUID tenantId, String name);
}
