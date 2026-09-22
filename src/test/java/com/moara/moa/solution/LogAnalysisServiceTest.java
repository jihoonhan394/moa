package com.moara.moa.solution;

import static org.assertj.core.api.Assertions.assertThat;

import com.moara.moa.solution.LogAnalysisService.ErrorGroup;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 로그 선필터·중복접기(순수 함수) 검증 — AI/원격 없이. 비용 가드의 핵심:
 * 정상 로그는 오류 0건(→ analyze가 AI 미호출), 반복 오류는 ×N으로 접힌다.
 */
class LogAnalysisServiceTest {
  private final LogAnalysisService service = new LogAnalysisService(null, null);

  @Test
  void cleanLogYieldsNoErrors() {
    String log = "INFO app started\nDEBUG cache warm\nINFO request handled ok\nINFO 200 GET /health";
    assertThat(service.digestErrors(log)).isEmpty(); // → analyze가 AI를 부르지 않음(0토큰)
  }

  @Test
  void repeatedIdenticalErrorsCollapseToOneGroupWithCount() {
    String log = """
        2026-01-01 10:00:00 ERROR NullPointerException at Foo.bar
        2026-01-01 10:00:01 INFO ok
        2026-01-01 10:00:02 ERROR NullPointerException at Foo.bar
        2026-01-01 10:00:03 ERROR NullPointerException at Foo.bar
        """;
    List<ErrorGroup> groups = service.digestErrors(log);
    assertThat(groups).hasSize(1);
    assertThat(groups.get(0).count()).isEqualTo(3);
  }

  @Test
  void attachesStackTraceAndSeparatesDistinctErrors() {
    String log = """
        10:00 ERROR java.lang.RuntimeException: boom
            at com.x.Foo.bar(Foo.java:10)
            at com.x.Baz.qux(Baz.java:20)
        10:01 INFO recovered
        10:02 FATAL OutOfMemory: heap space exhausted
        10:03 INFO plain informational line about a retry
        """;
    List<ErrorGroup> groups = service.digestErrors(log);
    assertThat(groups).hasSize(2); // RuntimeException 블록 + OutOfMemory
    assertThat(groups.get(0).sample()).contains("at com.x.Foo.bar");
    assertThat(groups).anyMatch(g -> g.sample().contains("OutOfMemory"));
  }
}
