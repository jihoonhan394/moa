package com.moara.moa.connection;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * append-only 이력. 서비스/컨트롤러에서 UPDATE/DELETE를 노출하지 않는다(감사 불변).
 */
public interface ConnectionEventRepository extends JpaRepository<ConnectionEvent, UUID> {
  List<ConnectionEvent> findAllByTenantIdAndConnectionSessionIdOrderByCreatedAtAsc(
      UUID tenantId, UUID connectionSessionId);

  List<ConnectionEvent> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
