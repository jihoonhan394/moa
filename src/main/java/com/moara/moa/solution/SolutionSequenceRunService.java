package com.moara.moa.solution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 기동 순서 실행기. 각 단계는 "대상 솔루션 + 동작(시작/중지/재시작)"이다.
 * 정방향(reverse=false): 등록 순서대로 각 단계의 동작을 그대로 실행. 예: 그룹웨어 중단 = 아파치 중지 →
 *   톰캣 중지 → exe 중지 → 문서변환 중지. 또는 페일오버 = A서버들 중지 → B서버들 시작.
 * 역방향(reverse=true): 역순으로 각 단계의 반대 동작(시작↔중지) 실행. 위 예의 "시작"·"복구"에 해당.
 * 한 단계라도 실패하면 그 지점에서 멈추고(이후 건너뜀) 에러를 보여준다. 시작 성공 후 wait_seconds만큼
 * 대기하며("좀 있다"), 옵션으로 STATUS 확인이 성공해야 다음으로 넘어간다. control()을 재사용하므로
 * 서로 다른 서버라도 각자의 호스트로 원격 실행된다. 동기 실행(대기는 120초로 제한).
 */
@Service
public class SolutionSequenceRunService {
  @org.springframework.beans.factory.annotation.Value("${moa.sequence.max-wait-seconds:120}")
  private int maxWaitSeconds;

  private final SolutionSequenceService sequenceService;
  private final ManagedSolutionService solutionService;
  private final SolutionControlService controlService;

  public SolutionSequenceRunService(
      SolutionSequenceService sequenceService, ManagedSolutionService solutionService,
      SolutionControlService controlService) {
    this.sequenceService = sequenceService;
    this.solutionService = solutionService;
    this.controlService = controlService;
  }

  public SequenceRunResult run(UUID tenantId, UUID sequenceId, boolean reverse) {
    sequenceService.findById(tenantId, sequenceId); // 소유·존재 검증
    List<SolutionSequenceStep> ordered = new ArrayList<>(sequenceService.steps(tenantId, sequenceId));
    if (reverse) {
      Collections.reverse(ordered);
    }
    List<SequenceStepOutcome> outcomes = new ArrayList<>();
    boolean halted = false;
    boolean overall = true;
    int order = 1;
    for (SolutionSequenceStep step : ordered) {
      String name = solutionName(tenantId, step.getSolutionId());
      ControlAction act = reverse ? invert(step.getAction()) : step.getAction();
      if (halted) {
        outcomes.add(new SequenceStepOutcome(order++, name, act, false, true, "이전 단계 실패로 건너뜀(대기)"));
        overall = false;
        continue;
      }
      SequenceStepOutcome outcome;
      try {
        ControlResult result = controlService.control(tenantId, step.getSolutionId(), act);
        boolean ok = result.success();
        String output = result.output();
        // 성공했으면 다음 단계 전 대기("좀 있다").
        if (ok && step.getWaitSeconds() > 0) {
          sleep(step.getWaitSeconds());
        }
        // 시작 동작이면 옵션으로 실제 기동됐는지 STATUS로 게이팅.
        if (ok && act == ControlAction.START && step.isVerifyAfterStart()) {
          ControlResult status = controlService.control(tenantId, step.getSolutionId(), ControlAction.STATUS);
          if (status.success()) {
            output = output + " | 상태확인 OK";
          } else {
            ok = false;
            output = "시작 후 상태 확인 실패: " + status.output();
          }
        }
        outcome = new SequenceStepOutcome(order, name, act, ok, false, output);
      } catch (RuntimeException exception) {
        outcome = new SequenceStepOutcome(order, name, act, false, false, exception.getMessage());
      }
      outcomes.add(outcome);
      order++;
      if (!outcome.success()) {
        overall = false;
        halted = true; // 문제 발생 → 멈추고 대기(이후 단계 건너뜀)
      }
    }
    return new SequenceRunResult(reverse, overall, outcomes);
  }

  private static ControlAction invert(ControlAction action) {
    return switch (action) {
      case START -> ControlAction.STOP;
      case STOP -> ControlAction.START;
      default -> action; // RESTART/STATUS는 그대로
    };
  }

  private String solutionName(UUID tenantId, UUID solutionId) {
    try {
      return solutionService.findById(tenantId, solutionId).getName();
    } catch (RuntimeException exception) {
      return "(알 수 없음)";
    }
  }

  private void sleep(int seconds) {
    try {
      Thread.sleep(Math.min(seconds, maxWaitSeconds) * 1000L);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
