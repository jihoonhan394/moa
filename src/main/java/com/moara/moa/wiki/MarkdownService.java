package com.moara.moa.wiki;

import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

/**
 * 위키 마크다운 렌더(다층 방어). (1) commonmark가 원문의 raw HTML을 이스케이프하고 URL을 새니타이즈해
 * 위험 HTML이 출력에 도달조차 못 하게 하고, (2) jsoup(최신, CVE 패치 포함) Safelist로 결과 HTML을 한 번 더
 * 정제한다. 이 안전 HTML만 화면에서 th:utext로 출력한다(원문은 평문 저장 유지).
 * GFM 확장(표·취소선·자동링크)을 적용해 폼이 안내하는 표 문법이 실제로 렌더되게 한다.
 */
@Service
public class MarkdownService {
  // 표(table/thead/tbody/tr/th/td)·정렬(align)·취소선(del)까지 허용하도록 relaxed에 보강.
  private static final Safelist SAFELIST = Safelist.relaxed()
      .addTags("table", "thead", "tbody", "tr", "th", "td", "del")
      .addAttributes("th", "align")
      .addAttributes("td", "align");

  private final List<Extension> extensions = List.of(
      TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create());
  private final Parser parser = Parser.builder().extensions(extensions).build();
  private final HtmlRenderer renderer = HtmlRenderer.builder()
      .extensions(extensions).escapeHtml(true).sanitizeUrls(true).build();

  public String toSafeHtml(String markdown) {
    if (markdown == null || markdown.isBlank()) {
      return "";
    }
    String rawHtml = renderer.render(parser.parse(markdown));
    // relaxed+표: 제목·목록·표·코드·링크·이미지 허용, script/onclick/javascript: 등은 제거.
    return Jsoup.clean(rawHtml, SAFELIST);
  }
}
