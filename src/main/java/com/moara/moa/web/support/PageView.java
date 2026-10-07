package com.moara.moa.web.support;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;

/**
 * 목록 화면의 페이지 이동에 필요한 것만 담는다. 화면은 {@code fragments/pager} 하나만 쓴다.
 *
 * <h2>왜 조각 하나로 모으나</h2>
 *
 * 감사·자산·사용자·솔루션 네 화면에 같은 마크업을 복사하면 <b>반드시 갈라진다</b> — 이
 * 세션에서 그 실패를 여러 번 봤다(분류 검사 네 벌, 404 등록 목록, 승인 집계 두 벌).
 *
 * <h2>왜 질의 문자열을 서버가 만드나</h2>
 *
 * 페이지를 넘길 때 <b>검색·필터 조건이 함께 따라가야 한다.</b> 안 그러면 2페이지로 가는
 * 순간 조건이 풀려 전체 목록이 나온다 — 사용자는 자기가 뭘 눌렀는지 알 수 없다.
 * 화면마다 조건 이름이 다르므로({@code q}·{@code status}·{@code role}…) 템플릿에서
 * 일일이 잇지 않고, 컨트롤러가 가진 값으로 한 번 만들어 넘긴다.
 *
 * @param pageNumber 0부터
 * @param totalPages 전체 페이지 수
 * @param totalCount 조건에 맞는 전체 건수(현재 페이지가 아니라)
 * @param queryBase {@code page=} 앞까지의 링크 — 예 {@code /inventory?q=%EB%85%B8&}
 */
public record PageView(int pageNumber, int totalPages, long totalCount, String queryBase) {

  /** 여러 페이지에 걸쳐 있나. 한 페이지면 이동 칸을 아예 그리지 않는다. */
  public boolean isPaged() {
    return totalPages > 1;
  }

  public boolean hasPrevious() {
    return pageNumber > 0;
  }

  public boolean hasNext() {
    return pageNumber + 1 < totalPages;
  }

  /** 사람에게 보여 줄 번호(1부터). */
  public int displayNumber() {
    return pageNumber + 1;
  }

  public String previousLink() {
    return queryBase + "page=" + (pageNumber - 1);
  }

  public String nextLink() {
    return queryBase + "page=" + (pageNumber + 1);
  }

  /**
   * {@link Page} 와 현재 조건으로 만든다.
   *
   * @param path 목록 경로 — 예 {@code /inventory}
   * @param conditions 함께 따라가야 하는 조건. 값이 {@code null} 이거나 비면 뺀다
   */
  public static PageView of(Page<?> page, String path, Map<String, ?> conditions) {
    StringBuilder base = new StringBuilder(path).append('?');
    for (Map.Entry<String, ?> entry : conditions.entrySet()) {
      Object value = entry.getValue();
      if (value == null || String.valueOf(value).isBlank()) {
        continue;
      }
      base.append(encode(entry.getKey())).append('=').append(encode(String.valueOf(value)))
          .append('&');
    }
    return new PageView(page.getNumber(), page.getTotalPages(), page.getTotalElements(),
        base.toString());
  }

  /** 조건 이름·값을 순서대로 담는다. 링크가 매번 같은 순서로 나와 비교하기 쉽다. */
  public static Map<String, Object> conditions(Object... keyValues) {
    if (keyValues.length % 2 != 0) {
      throw new IllegalArgumentException("조건은 이름과 값이 짝이어야 합니다.");
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (int index = 0; index < keyValues.length; index += 2) {
      map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
    }
    return map;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
