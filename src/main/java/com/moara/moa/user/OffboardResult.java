package com.moara.moa.user;

import java.util.List;

/** 퇴사 처리로 회수·정리된 내역(모듈별 결과 목록). total()은 전체 건수 합. */
public record OffboardResult(List<OffboardOutcome> outcomes) {
  public long total() {
    return outcomes.stream().mapToLong(OffboardOutcome::count).sum();
  }
}
