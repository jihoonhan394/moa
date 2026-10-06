package com.moara.moa.solution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 헬스 체크 배치. 판정·알림은 {@link SolutionHealthService} 에 있고 여기서는 주기만 정한다.
 *
 * <p>{@code fixedDelay} 다({@code fixedRate} 가 아니다) — 이전 실행이 끝난 뒤부터 센다.
 * 솔루션이 많거나 느린 호스트가 섞이면 한 바퀴가 간격보다 길어질 수 있는데, {@code fixedRate}
 * 면 그때 실행이 겹쳐 같은 솔루션을 두 스레드가 동시에 쓰고 알림이 두 번 나간다.
 */
@Component
public class SolutionHealthScheduler {

  private static final Logger log = LoggerFactory.getLogger(SolutionHealthScheduler.class);

  private final SolutionHealthService healthService;
  private final boolean enabled;

  public SolutionHealthScheduler(
      SolutionHealthService healthService,
      @Value("${moa.solution.health-enabled:true}") boolean enabled) {
    this.healthService = healthService;
    this.enabled = enabled;
  }

  /**
   * 기본 1분. 연속 실패 기준이 2회이므로 장애 감지까지 약 2분이다.
   *
   * <p>{@code moa.solution.health-enabled=false} 로 끈다. 간격을 크게 두는 것으로 끄지
   * 않는 이유는, 그래도 기동 직후 한 번은 돌아서 테스트·시연 중에 알림이 끼어들기 때문이다.
   */
  @Scheduled(fixedDelayString = "${moa.solution.health-interval-ms:60000}")
  public void run() {
    if (!enabled) {
      return;
    }
    int alerts = healthService.checkAllTenants();
    if (alerts > 0) {
      log.info("[HEALTH] {} 건의 상태 변경을 알렸다", alerts);
    }
  }
}
