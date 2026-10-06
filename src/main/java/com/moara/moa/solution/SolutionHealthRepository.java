package com.moara.moa.solution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolutionHealthRepository extends JpaRepository<SolutionHealth, UUID> {

  Optional<SolutionHealth> findByTenantIdAndSolutionId(UUID tenantId, UUID solutionId);

  /** 배치·화면이 기관의 상태를 한 번에 읽는다. 솔루션마다 단건 조회하면 질의가 N 번 나간다. */
  List<SolutionHealth> findAllByTenantId(UUID tenantId);

  List<SolutionHealth> findAllByTenantIdAndState(UUID tenantId, HealthState state);

  long countByTenantIdAndState(UUID tenantId, HealthState state);
}
