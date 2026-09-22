package com.moara.moa.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
  Optional<Tenant> findByCode(String code);

  boolean existsByCodeIgnoreCase(String code);

  List<Tenant> findAllByOrderByCodeAsc();

  long countByStatus(TenantStatus status);
}
