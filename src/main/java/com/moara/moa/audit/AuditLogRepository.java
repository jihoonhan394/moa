package com.moara.moa.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * append-only 감사 로그. 서비스/컨트롤러에서 UPDATE/DELETE를 노출하지 않는다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
  /**
   * 화면용 목록은 <b>전부 페이지로만</b> 꺼낸다.
   *
   * <p>⚠️ 전에는 {@code findAllByTenantIdOrderByCreatedAtDesc} 로 기관의 감사 로그를
   * <b>전부</b> 읽어 한 페이지에 그렸다. 감사 로그는 특권 행위마다 한 줄 쌓이고 지우지
   * 않으므로 1년이면 수만 건이 된다 — 그때 그 화면은 느려지는 게 아니라 <b>열리지 않는다.</b>
   * 무제한 조회 메서드를 남겨 두면 다음 화면이 또 그걸 쓰므로 지웠다.
   *
   * <p>{@code (tenant_id, created_at)} 인덱스가 V8 부터 있어 정렬·구간 조회가 그걸 탄다.
   */
  Page<AuditLog> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

  Page<AuditLog> findAllByTenantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
      UUID tenantId, OffsetDateTime since, Pageable pageable);

  Page<AuditLog> findAllByActionScopeOrderByCreatedAtDesc(
      AuditActionScope actionScope, Pageable pageable);

  /**
   * 한 사람의 특정 행위 최근 N 건.
   *
   * <p>⚠️ 전에는 그 사람의 감사 로그를 <b>전부</b> 읽어 메모리에서 action 을 거르고 10건을
   * 잘랐다(포털의 "최근 웹 접속"). 많이 쓰는 사람일수록 느려지는, 거꾸로 된 구조였다.
   */
  List<AuditLog> findTop10ByActorUserIdAndActionOrderByCreatedAtDesc(
      UUID actorUserId, String action);

  List<AuditLog> findAllByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(
      UUID tenantId, String targetType, UUID targetId);

  List<AuditLog> findTop100ByTenantIdAndActionOrderByCreatedAtDesc(UUID tenantId, String action);

  /**
   * 하루치 조회(일 요약용). 전체를 읽어 메모리에서 거르면 로그가 쌓일수록 느려진다 —
   * 감사 로그는 지우지 않으므로 계속 는다.
   */
  List<AuditLog> findAllByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
      UUID tenantId, java.time.OffsetDateTime from, java.time.OffsetDateTime to);
}
