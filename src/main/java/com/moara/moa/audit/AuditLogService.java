package com.moara.moa.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 행위 감사 로그 기록/조회. append-only — 수정/삭제 API를 제공하지 않는다.
 * 다른 서비스(사용자/그룹/권한 관리 등)가 성공/실패 행위를 이 서비스로 남긴다.
 */
@Service
@Transactional(readOnly = true)
public class AuditLogService {
  private final AuditLogRepository auditLogRepository;
  private final Clock clock;

  @Autowired
  public AuditLogService(AuditLogRepository auditLogRepository) {
    this(auditLogRepository, Clock.systemUTC());
  }

  AuditLogService(AuditLogRepository auditLogRepository, Clock clock) {
    this.auditLogRepository = auditLogRepository;
    this.clock = clock;
  }

  /** 테넌트 내 관리자 행위를 기록한다. */
  @Transactional
  public AuditLog recordTenantAction(
      UUID tenantId, UUID actorUserId, String action, String targetType, UUID targetId,
      AuditResult result, String message) {
    return auditLogRepository.save(AuditLog.tenantAction(
        UUID.randomUUID(), tenantId, actorUserId, action, targetType, targetId,
        result, message, OffsetDateTime.now(clock)));
  }

  /** SYSTEM_ADMIN의 전역 행위를 기록한다(특정 테넌트 대상이면 targetTenantId 지정). */
  @Transactional
  public AuditLog recordGlobalAction(
      UUID actorUserId, UUID targetTenantId, String action, String targetType, UUID targetId,
      AuditResult result, String message) {
    return auditLogRepository.save(AuditLog.globalAction(
        UUID.randomUUID(), actorUserId, targetTenantId, action, targetType, targetId,
        result, message, OffsetDateTime.now(clock)));
  }

  public List<AuditLog> findByTenant(UUID tenantId) {
    return auditLogRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
  }

  public List<AuditLog> findGlobal() {
    return auditLogRepository.findAllByActionScopeOrderByCreatedAtDesc(AuditActionScope.GLOBAL);
  }

  public List<AuditLog> findByActor(UUID actorUserId) {
    return auditLogRepository.findAllByActorUserIdOrderByCreatedAtDesc(actorUserId);
  }

  /** 특정 대상(자원)의 변경 이력. 최신순. */
  public List<AuditLog> findByTarget(UUID tenantId, String targetType, UUID targetId) {
    return auditLogRepository.findAllByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(
        tenantId, targetType, targetId);
  }

  /** 특정 액션(예: USER_LOGIN)의 최근 100건. 최신순. */
  public List<AuditLog> findRecentByAction(UUID tenantId, String action) {
    return auditLogRepository.findTop100ByTenantIdAndActionOrderByCreatedAtDesc(tenantId, action);
  }
}
