package com.moara.moa.solution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedSolutionRepository extends JpaRepository<ManagedSolution, UUID> {
  List<ManagedSolution> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  List<ManagedSolution> findAllByTenantIdAndAssetIdOrderByNameAsc(UUID tenantId, UUID assetId);

  Optional<ManagedSolution> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<ManagedSolution> findByTenantIdAndAssetIdAndName(UUID tenantId, UUID assetId, String name);

  long countByTenantIdAndCredentialId(UUID tenantId, UUID credentialId);
}
