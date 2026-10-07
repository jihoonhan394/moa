package com.moara.moa.web.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 페이지 이동 링크를 만드는 규칙.
 *
 * <p>핵심은 <b>검색·필터 조건이 페이지를 넘어도 따라가는 것</b>이다. 안 따라가면 2페이지로
 * 가는 순간 조건이 풀려 전체 목록이 나오고, 사용자는 자기가 뭘 눌렀는지 알 수 없다.
 */
class PageViewTest {

  private static PageView view(int page, int totalElements, java.util.Map<String, ?> conditions) {
    List<String> rows = List.of("a");
    return PageView.of(
        new PageImpl<>(rows, PageRequest.of(page, 50), totalElements), "/inventory", conditions);
  }

  @Test
  @DisplayName("⚠️ 조건이 링크에 실려 따라간다 — 안 그러면 2페이지에서 검색이 풀린다")
  void 조건이_따라간다() {
    PageView pager = view(0, 200, PageView.conditions("q", "노트북", "status", "AVAILABLE"));

    assertThat(pager.nextLink())
        .startsWith("/inventory?")
        .contains("status=AVAILABLE")
        .endsWith("page=1");
    // 한글은 인코딩된다 — 날로 넣으면 링크가 깨진다.
    assertThat(pager.nextLink()).contains("q=%EB%85%B8%ED%8A%B8%EB%B6%81");
  }

  @Test
  @DisplayName("비어 있는 조건은 링크에 넣지 않는다 — q= 같은 빈 칸이 쌓이면 읽을 수 없다")
  void 빈_조건은_빠진다() {
    PageView pager = view(0, 200, PageView.conditions(
        "q", null, "status", "", "type", "  ", "showRetired", "true"));

    assertThat(pager.nextLink()).isEqualTo("/inventory?showRetired=true&page=1");
  }

  @Test
  @DisplayName("한 페이지면 이동 칸을 그리지 않는다")
  void 한_페이지면_안_그린다() {
    assertThat(view(0, 10, PageView.conditions()).isPaged()).isFalse();
    assertThat(view(0, 200, PageView.conditions()).isPaged()).isTrue();
  }

  @Test
  @DisplayName("처음·마지막 페이지에서는 그 방향 링크를 내지 않는다")
  void 양_끝에서는_한쪽만() {
    PageView first = view(0, 200, PageView.conditions());
    assertThat(first.hasPrevious()).isFalse();
    assertThat(first.hasNext()).isTrue();

    PageView last = view(3, 200, PageView.conditions());
    assertThat(last.hasPrevious()).isTrue();
    assertThat(last.hasNext()).isFalse();
  }

  @Test
  @DisplayName("사람에게 보여 줄 번호는 1부터다 — 0페이지라고 쓰면 안 된다")
  void 번호는_1부터다() {
    assertThat(view(0, 200, PageView.conditions()).displayNumber()).isEqualTo(1);
    assertThat(view(2, 200, PageView.conditions()).displayNumber()).isEqualTo(3);
  }

  @Test
  @DisplayName("전체 건수는 현재 페이지가 아니라 조건에 맞는 전부다")
  void 전체_건수를_센다() {
    PageView pager = view(0, 237, PageView.conditions());
    assertThat(pager.totalCount()).isEqualTo(237);
    assertThat(pager.totalPages()).isEqualTo(5);
  }

  @Test
  @DisplayName("조건 이름·값이 짝이 아니면 바로 터진다 — 조용히 하나를 버리면 안 된다")
  void 짝이_안_맞으면_거부한다() {
    assertThatThrownBy(() -> PageView.conditions("q", "노트북", "status"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
