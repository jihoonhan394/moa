package com.moara.moa.audit;

import com.moara.moa.security.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 기관 범위 감사 기록의 공통 진입점.
 *
 * <p>전에는 컨트롤러 <b>23곳</b>이 같은 5줄을 각자 갖고 있었다 — 현재 사용자를 꺼내 null이
 * 아니면 {@link AuditLogService#recordTenantAction}을 부르는 코드로, 도메인마다 targetType
 * 문자열만 달랐다.
 *
 * <p>모아야 하는 이유는 줄 수가 아니라 <b>정책이 한 곳에 있어야 한다</b>는 것이다.
 * "행위자를 모르면 기록하지 않는다", "기본 결과는 성공"과 같은 판단이 23곳에 흩어져 있으면
 * 정책이 바뀔 때 전부 찾아야 하고, 하나를 빠뜨려도 아무도 모른다. 실제로 0.7.7에서
 * 감사 누락분을 보완한 적이 있다.
 *
 * <p>컨트롤러는 자기 {@code private void audit(...)}를 남겨 두고 본문만 이리로 위임한다 —
 * 호출부(수십 곳)를 건드리지 않으므로 이 리팩토링 자체가 회귀를 만들지 않는다.
 */
@Component
public class TenantAuditRecorder {
  private final AuditLogService auditLogService;
  private final TenantContext tenantContext;

  public TenantAuditRecorder(AuditLogService auditLogService, TenantContext tenantContext) {
    this.auditLogService = auditLogService;
    this.tenantContext = tenantContext;
  }

  /** 성공한 특권 행위. */
  public void record(String targetType, String action, UUID targetId, String message) {
    record(targetType, action, targetId, AuditResult.SUCCESS, message);
  }

  /**
   * 결과를 명시해 기록한다. <b>실패도 남겨야 한다</b> — 성공만 기록하면 "왜 안 됐나"를
   * 사후에 설명할 수 없고, 거부된 시도가 반복되는 패턴도 보이지 않는다.
   *
   * <p>행위자를 모르면(배치·미인증 경로) 기록하지 않는다. 누가 했는지 없는 감사 기록은
   * 나중에 아무 질문에도 답하지 못하면서 목록만 채운다 — 그런 흐름은 도메인 쪽에서
   * 사유를 남기는 편이 낫다(예: 보관 장부의 "퇴사 회수").
   */
  public void record(
      String targetType, String action, UUID targetId, AuditResult result, String message) {
    UUID actorId = tenantContext.currentUserId();
    if (actorId == null) {
      return;
    }
    auditLogService.recordTenantAction(
        tenantContext.currentTenantId(), actorId, action, targetType, targetId, result, message);
  }

  /** 실패한 특권 행위. 무엇이 막혔는지 메시지에 남긴다. */
  public void recordFailure(String targetType, String action, UUID targetId, String message) {
    record(targetType, action, targetId, AuditResult.FAILURE, message);
  }
}
