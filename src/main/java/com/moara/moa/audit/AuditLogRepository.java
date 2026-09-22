package com.moara.moa.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * append-only 감사 로그. 서비스/컨트롤러에서 UPDATE/DELETE를 노출하지 않는다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
  List<AuditLog> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  List<AuditLog> findAllByActionScopeOrderByCreatedAtDesc(AuditActionScope actionScope);

  List<AuditLog> findAllByActorUserIdOrderByCreatedAtDesc(UUID actorUserId);

  List<AuditLog> findAllByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(
      UUID tenantId, String targetType, UUID targetId);

  List<AuditLog> findTop100ByTenantIdAndActionOrderByCreatedAtDesc(UUID tenantId, String action);
}
