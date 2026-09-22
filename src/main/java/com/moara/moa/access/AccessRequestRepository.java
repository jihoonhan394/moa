package com.moara.moa.access;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 접근요청 영속성. 모든 조회는 tenant_id로 스코프한다(교차테넌트 차단). */
public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

  Optional<AccessRequest> findByIdAndTenantId(UUID id, UUID tenantId);

  /** 요청자 본인 목록(최신순). */
  List<AccessRequest> findByTenantIdAndRequesterUserIdOrderByCreatedAtDesc(UUID tenantId, UUID requesterUserId);

  /** 특정 상태 목록(최신순) — 승인 대기 목록 등. */
  List<AccessRequest> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, AccessRequestStatus status);

  /** 활성 승인 존재 여부(런타임 접속 게이트): 승인됨 + 현재가 [시작,종료) 창 안. */
  @Query("""
      SELECT COUNT(r) FROM AccessRequest r
      WHERE r.tenantId = :tenantId AND r.requesterUserId = :userId AND r.assetId = :assetId
        AND r.status = com.moara.moa.access.AccessRequestStatus.APPROVED
        AND r.requestedStartAt <= :now AND r.requestedEndAt > :now
      """)
  long countActiveApprovals(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
      @Param("assetId") UUID assetId, @Param("now") OffsetDateTime now);

  /** 활성 승인 1건(자격증명 주입 접속용). 여러 건이면 가장 최근 승인. */
  @Query("""
      SELECT r FROM AccessRequest r
      WHERE r.tenantId = :tenantId AND r.requesterUserId = :userId AND r.assetId = :assetId
        AND r.status = com.moara.moa.access.AccessRequestStatus.APPROVED
        AND r.requestedStartAt <= :now AND r.requestedEndAt > :now
      ORDER BY r.decidedAt DESC
      """)
  List<AccessRequest> findActiveApprovals(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
      @Param("assetId") UUID assetId, @Param("now") OffsetDateTime now);

  /** 만료 배치: 종료 시각이 지난 승인 건(전 기관). 스케줄러 전용. */
  @Query("""
      SELECT r FROM AccessRequest r
      WHERE r.status = com.moara.moa.access.AccessRequestStatus.APPROVED AND r.requestedEndAt <= :now
      """)
  List<AccessRequest> findExpiredApprovals(@Param("now") OffsetDateTime now);
}
