package com.moara.moa.remote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RemoteHostKeyRepository extends JpaRepository<RemoteHostKey, UUID> {
  Optional<RemoteHostKey> findByTenantIdAndHostAndPortAndChannel(
      UUID tenantId, String host, int port, RemoteChannel channel);

  List<RemoteHostKey> findAllByTenantIdOrderByHostAsc(UUID tenantId);

  void deleteByTenantIdAndHostAndPort(UUID tenantId, String host, int port);
}
