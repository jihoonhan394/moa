package com.moara.moa.solution;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * cron 스케줄에 따라 기동 순서를 자동 시작/중지한다. 매 분(fixedDelay 60s) 모든 시퀀스의 cron을 평가해
 * 지금 발화해야 할 것을 실행한다. 같은 예정 시각에 중복 발화하지 않도록 마지막 발화 키를 저장한다.
 * cron은 Spring 6필드(초 분 시 일 월 요일). 예: "0 0 9 * * *" = 매일 09:00.
 */
@Component
public class SequenceScheduler {
  private static final Logger log = LoggerFactory.getLogger(SequenceScheduler.class);

  private final SolutionSequenceRepository sequenceRepository;
  private final SolutionSequenceRunService runService;

  public SequenceScheduler(
      SolutionSequenceRepository sequenceRepository, SolutionSequenceRunService runService) {
    this.sequenceRepository = sequenceRepository;
    this.runService = runService;
  }

  @Scheduled(fixedDelayString = "${moa.sequence.scheduler-interval-ms:60000}")
  public void tick() {
    LocalDateTime now = LocalDateTime.now();
    for (SolutionSequence sequence : sequenceRepository.findAll()) {
      evaluate(sequence, sequence.getForwardCron(), sequence.getLastForwardFired(), now, false);
      evaluate(sequence, sequence.getReverseCron(), sequence.getLastReverseFired(), now, true);
    }
  }

  private void evaluate(
      SolutionSequence sequence, String cron, String lastFired, LocalDateTime now, boolean reverse) {
    if (cron == null || cron.isBlank()) {
      return;
    }
    LocalDateTime due;
    try {
      due = CronExpression.parse(cron.trim()).next(now.minusSeconds(65));
    } catch (RuntimeException exception) {
      return; // 잘못된 cron은 무시(등록 시 검증됨)
    }
    if (due == null || due.isAfter(now)) {
      return; // 아직 예정 시각 전
    }
    String key = due.withNano(0).toString();
    if (key.equals(lastFired)) {
      return; // 이 예정 시각엔 이미 발화함
    }
    // 발화 키를 먼저 저장(중복/재시도 폭주 방지), 그다음 실행.
    if (reverse) {
      sequence.markReverseFired(key);
    } else {
      sequence.markForwardFired(key);
    }
    sequenceRepository.save(sequence);
    try {
      runService.run(sequence.getTenantId(), sequence.getId(), reverse);
      log.info("Scheduled {} run of sequence {} ({})", reverse ? "reverse" : "forward", sequence.getName(), key);
    } catch (RuntimeException exception) {
      log.warn("Scheduled run of sequence {} failed: {}", sequence.getId(), exception.getMessage());
    }
  }
}
