package com.moara.moa.user;

import java.util.List;

/** 퇴사 처리로 회수·정리된 내역(모듈별 결과 목록). total()은 전체 건수 합. */
public record OffboardResult(List<OffboardOutcome> outcomes) {
  public long total() {
    return outcomes.stream().mapToLong(OffboardOutcome::count).sum();
  }

  /** 그 자리에서 끝난 회수. */
  public List<OffboardOutcome> done() {
    return outcomes.stream().filter(outcome -> !outcome.pending()).toList();
  }

  /** 후속 확인이 남은 회수(건수가 있는 것만). */
  public List<OffboardOutcome> awaiting() {
    return outcomes.stream().filter(outcome -> outcome.pending() && outcome.count() > 0).toList();
  }
}
