package com.moara.moa.expiration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만료 임박 알림 일 배치. 화면이 안내하는 "임박 시 알림"을 실제로 발송한다.
 * 판정·발송 로직은 {@link ExpirationAlertService}에 있고 여기서는 실행 시각만 정한다.
 */
@Component
public class ExpirationAlertScheduler {
  private static final Logger log = LoggerFactory.getLogger(ExpirationAlertScheduler.class);

  private final ExpirationAlertService alertService;

  public ExpirationAlertScheduler(ExpirationAlertService alertService) {
    this.alertService = alertService;
  }

  /** 매일 09:00(Asia/Seoul) — 업무 시작 시각에 받도록. 배치 시각은 프로퍼티로 조정 가능. */
  @Scheduled(cron = "${moa.expiration.alert-cron:0 0 9 * * *}", zone = "Asia/Seoul")
  public void run() {
    int sent = alertService.notifyAllTenants();
    if (sent > 0) {
      log.info("[EXPIRATION] sent {} expiry alert notification(s)", sent);
    }
  }
}
