package com.moara.moa.reservation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedResourceRepository extends JpaRepository<SharedResource, UUID> {
  List<SharedResource> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<SharedResource> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<SharedResource> findByTenantIdAndName(UUID tenantId, String name);
}
