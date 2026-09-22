package com.moara.moa.wiki;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 위키 마크다운 렌더: GFM 확장(표·취소선) 동작과 XSS 새니타이즈를 확인한다. */
class MarkdownServiceTest {
  private final MarkdownService service = new MarkdownService();

  @Test
  void rendersGfmTable() {
    String html = service.toSafeHtml("| A | B |\n| --- | --- |\n| 1 | 2 |");
    assertThat(html).contains("<table").contains("<td>1</td>").contains("<td>2</td>");
  }

  @Test
  void rendersStrikethrough() {
    assertThat(service.toSafeHtml("~~gone~~")).contains("<del>");
  }

  @Test
  void keepsBoldButStripsScript() {
    String html = service.toSafeHtml("**hi** <script>alert(1)</script>");
    assertThat(html).contains("<strong>hi</strong>").doesNotContain("<script>");
  }

  @Test
  void blankReturnsEmpty() {
    assertThat(service.toSafeHtml("")).isEmpty();
    assertThat(service.toSafeHtml(null)).isEmpty();
  }
}
