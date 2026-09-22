package com.moara.moa.access;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 승인 만료 배치. 종료 시각이 지난 승인 건을 EXPIRED로 전이(자동 반납)한다. 런타임 접속 게이트는
 * 시각 창으로 이미 즉시 차단하므로, 이 배치는 상태 표시 정합성을 맞추는 청소 작업이다.
 */
@Component
public class AccessRequestExpiryScheduler {
  private static final Logger log = LoggerFactory.getLogger(AccessRequestExpiryScheduler.class);

  private final AccessApprovalService approvalService;

  public AccessRequestExpiryScheduler(AccessApprovalService approvalService) {
    this.approvalService = approvalService;
  }

  /** 기본 1분 간격(프로퍼티로 조정). */
  @Scheduled(fixedDelayString = "${moa.access.expiry-interval-ms:60000}")
  public void expire() {
    int expired = approvalService.expireDue();
    if (expired > 0) {
      log.info("[ACCESS] expired {} access grant(s)", expired);
    }
  }
}
