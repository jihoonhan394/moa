package com.moara.moa.consumable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumableRequestRepository extends JpaRepository<ConsumableRequest, UUID> {
  Optional<ConsumableRequest> findByTenantIdAndId(UUID tenantId, UUID id);

  List<ConsumableRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  List<ConsumableRequest> findByTenantIdAndRequestedByOrderByCreatedAtDesc(
      UUID tenantId, UUID requestedBy);

  List<ConsumableRequest> findByTenantIdAndStatusIn(
      UUID tenantId, java.util.Collection<ConsumableRequestStatus> statuses);

  List<ConsumableRequest> findByTenantIdAndItemIdAndStatusIn(
      UUID tenantId, UUID itemId, java.util.Collection<ConsumableRequestStatus> statuses);
}
