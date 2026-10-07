package com.moara.moa.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

  /** 화면에 보여 줄 페이지 크기. 한 화면에 전부 그리지 않는다. */
  public static final int PAGE_SIZE = 50;

  /**
   * 기관의 감사 로그 한 페이지.
   *
   * @param since 이 시각 이후만. {@code null} 이면 전 기간(그래도 페이지로만 나간다)
   */
  public Page<AuditLog> findByTenant(UUID tenantId, OffsetDateTime since, int page) {
    Pageable pageable = PageRequest.of(Math.max(0, page), PAGE_SIZE);
    return since == null
        ? auditLogRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
        : auditLogRepository.findAllByTenantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            tenantId, since, pageable);
  }

  /** 플랫폼 전역 행위 한 페이지. */
  public Page<AuditLog> findGlobal(int page) {
    return auditLogRepository.findAllByActionScopeOrderByCreatedAtDesc(
        AuditActionScope.GLOBAL, PageRequest.of(Math.max(0, page), PAGE_SIZE));
  }

  /** 한 사람의 특정 행위 최근 10건. 거르기와 자르기를 모두 질의가 한다. */
  public List<AuditLog> findRecentByActor(UUID actorUserId, String action) {
    return auditLogRepository.findTop10ByActorUserIdAndActionOrderByCreatedAtDesc(
        actorUserId, action);
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
