package com.moara.moa.deputy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeputyDelegationRepository extends JpaRepository<DeputyDelegation, UUID> {
  List<DeputyDelegation> findAllByTenantIdAndDeputyUserId(UUID tenantId, UUID deputyUserId);

  List<DeputyDelegation> findAllByTenantIdOrderByStartsOnDesc(UUID tenantId);

  Optional<DeputyDelegation> findByTenantIdAndId(UUID tenantId, UUID id);
}
