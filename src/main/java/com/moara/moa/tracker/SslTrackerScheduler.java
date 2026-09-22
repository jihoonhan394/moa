package com.moara.moa.tracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SSL 자동감지 트래커 일 배치. 저장된 host/port로 서버 인증서를 다시 읽어 만료일·상세를 갱신한다.
 * 수동 "인증서 만료일 감지"로 만든 항목만 대상(host/port 없는 과거 항목은 건너뜀). 발급·설치는 하지 않는다.
 */
@Component
public class SslTrackerScheduler {
  private static final Logger log = LoggerFactory.getLogger(SslTrackerScheduler.class);

  private final ResourceTrackerService trackerService;

  public SslTrackerScheduler(ResourceTrackerService trackerService) {
    this.trackerService = trackerService;
  }

  /** 매일 03:20(Asia/Seoul). 배치 시각은 프로퍼티로 조정 가능. */
  @Scheduled(cron = "${moa.tracker.ssl-refresh-cron:0 20 3 * * *}", zone = "Asia/Seoul")
  public void refresh() {
    int updated = trackerService.refreshSslTrackers();
    if (updated > 0) {
      log.info("[SSL] refreshed {} certificate tracker(s)", updated);
    }
  }
}
