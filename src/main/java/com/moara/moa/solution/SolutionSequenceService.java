package com.moara.moa.solution;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기동 순서(오케스트레이션) 관리 — 시퀀스 CRUD + 단계 추가/삭제/순서변경. 실행은
 * {@link SolutionSequenceRunService}가 맡는다. 모든 조회·변경은 현재 기관으로 스코프된다.
 */
@Service
@Transactional(readOnly = true)
public class SolutionSequenceService {
  @org.springframework.beans.factory.annotation.Value("${moa.sequence.max-wait-seconds:120}")
  private int maxWaitSeconds;

  private final SolutionSequenceRepository sequenceRepository;
  private final SolutionSequenceStepRepository stepRepository;
  private final ManagedSolutionService solutionService;

  public SolutionSequenceService(
      SolutionSequenceRepository sequenceRepository, SolutionSequenceStepRepository stepRepository,
      ManagedSolutionService solutionService) {
    this.sequenceRepository = sequenceRepository;
    this.stepRepository = stepRepository;
    this.solutionService = solutionService;
  }

  public List<SolutionSequence> findAll(UUID tenantId) {
    return sequenceRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public SolutionSequence findById(UUID tenantId, UUID id) {
    return sequenceRepository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new SolutionSequenceNotFoundException(id));
  }

  public List<SolutionSequenceStep> steps(UUID tenantId, UUID sequenceId) {
    return stepRepository.findAllByTenantIdAndSequenceIdOrderByPositionAsc(tenantId, sequenceId);
  }

  @Transactional
  public SolutionSequence create(UUID tenantId, SolutionSequenceForm form) {
    sequenceRepository.findByTenantIdAndName(tenantId, form.name()).ifPresent(existing -> {
      throw new DuplicateSolutionSequenceException(form.name());
    });
    return sequenceRepository.save(new SolutionSequence(UUID.randomUUID(), tenantId, form, OffsetDateTime.now()));
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    sequenceRepository.delete(findById(tenantId, id));
  }

  /** 단계 추가(맨 뒤). 대상 솔루션 + 동작(시작/중지/재시작). 솔루션이 기관 소유인지 검증. wait 0~120초 클램프. */
  @Transactional
  public SolutionSequenceStep addStep(
      UUID tenantId, UUID sequenceId, UUID solutionId, ControlAction action, int waitSeconds,
      boolean verifyAfterStart) {
    findById(tenantId, sequenceId);
    solutionService.findById(tenantId, solutionId); // 소유·존재 검증
    List<SolutionSequenceStep> existing = steps(tenantId, sequenceId);
    int nextPosition = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getPosition() + 1;
    int wait = Math.max(0, Math.min(waitSeconds, maxWaitSeconds));
    ControlAction effective = action == null ? ControlAction.START : action;
    return stepRepository.save(new SolutionSequenceStep(
        UUID.randomUUID(), tenantId, sequenceId, solutionId, effective, nextPosition, wait, verifyAfterStart,
        OffsetDateTime.now()));
  }

  /** cron 스케줄 설정(빈 값=해제). forward=정방향, reverse=역방향. 잘못된 cron은 거부. */
  @Transactional
  public void setSchedule(UUID tenantId, UUID sequenceId, String forwardCron, String reverseCron) {
    SolutionSequence sequence = findById(tenantId, sequenceId);
    validateCron(forwardCron);
    validateCron(reverseCron);
    sequence.setSchedule(forwardCron, reverseCron, OffsetDateTime.now());
    sequenceRepository.save(sequence);
  }

  private void validateCron(String cron) {
    if (cron != null && !cron.isBlank()) {
      try {
        org.springframework.scheduling.support.CronExpression.parse(cron.trim());
      } catch (IllegalArgumentException exception) {
        throw new InvalidCronException(cron);
      }
    }
  }

  @Transactional
  public void removeStep(UUID tenantId, UUID stepId) {
    SolutionSequenceStep step = stepRepository.findByTenantIdAndId(tenantId, stepId)
        .orElseThrow(() -> new SolutionSequenceNotFoundException(stepId));
    stepRepository.delete(step);
  }

  /** 단계를 한 칸 위/아래로(순서 스왑). */
  @Transactional
  public void move(UUID tenantId, UUID stepId, boolean up) {
    SolutionSequenceStep step = stepRepository.findByTenantIdAndId(tenantId, stepId)
        .orElseThrow(() -> new SolutionSequenceNotFoundException(stepId));
    List<SolutionSequenceStep> ordered = steps(tenantId, step.getSequenceId());
    int index = -1;
    for (int i = 0; i < ordered.size(); i++) {
      if (ordered.get(i).getId().equals(stepId)) {
        index = i;
        break;
      }
    }
    int neighbor = up ? index - 1 : index + 1;
    if (index < 0 || neighbor < 0 || neighbor >= ordered.size()) {
      return; // 경계 — 이동 없음
    }
    SolutionSequenceStep other = ordered.get(neighbor);
    int tmp = step.getPosition();
    step.setPosition(other.getPosition());
    other.setPosition(tmp);
    stepRepository.save(step);
    stepRepository.save(other);
  }
}
