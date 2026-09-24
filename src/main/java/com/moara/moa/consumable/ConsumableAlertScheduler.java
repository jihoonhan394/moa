package com.moara.moa.consumable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 소모품 주문 주기 알림 일 배치. 판정·발송은 {@link ConsumableAlertService}에 있고
 * 여기서는 실행 시각만 정한다.
 *
 * <p>만료 알림과 <b>같은 시각에 돈다</b>(09:00). 담당자가 아침에 한 번에 받아 보는 편이
 * 하루에 여러 번 나눠 받는 것보다 낫다.
 */
@Component
public class ConsumableAlertScheduler {
  private static final Logger log = LoggerFactory.getLogger(ConsumableAlertScheduler.class);

  private final ConsumableAlertService alertService;

  public ConsumableAlertScheduler(ConsumableAlertService alertService) {
    this.alertService = alertService;
  }

  @Scheduled(cron = "${moa.consumable.alert-cron:0 0 9 * * *}", zone = "Asia/Seoul")
  public void run() {
    int sent = alertService.notifyAllTenants();
    if (sent > 0) {
      log.info("[CONSUMABLE] sent {} reorder reminder(s)", sent);
    }
  }
}
