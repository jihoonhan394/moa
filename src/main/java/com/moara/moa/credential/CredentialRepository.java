package com.moara.moa.credential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {
  List<Credential> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<Credential> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Credential> findByTenantIdAndName(UUID tenantId, String name);
}
