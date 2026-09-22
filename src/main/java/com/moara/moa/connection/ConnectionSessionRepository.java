package com.moara.moa.connection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConnectionSessionRepository extends JpaRepository<ConnectionSession, UUID> {
  Optional<ConnectionSession> findByIdAndTenantId(UUID id, UUID tenantId);

  Optional<ConnectionSession> findBySessionId(String sessionId);

  List<ConnectionSession> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  List<ConnectionSession> findAllByTenantIdAndUserIdOrderByCreatedAtDesc(UUID tenantId, UUID userId);

  List<ConnectionSession> findAllByTenantIdAndUserIdAndStatus(
      UUID tenantId, UUID userId, ConnectionStatus status);
}
