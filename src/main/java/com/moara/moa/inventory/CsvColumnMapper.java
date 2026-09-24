package com.moara.moa.inventory;

import com.moara.moa.ai.AiService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 남의 엑셀 열 순서를 우리 순서로 맞춰 준다. <b>도입 장벽을 낮추는 것이 목적</b>이다 —
 * 자산관리 제품의 실제 진입 장벽은 기능이 아니라 "우리 엑셀을 어떻게 넣느냐"이고,
 * 기관마다 컬럼명이 자산번호/관리번호/태그, 사용자/담당자/사용부서로 다 다르다.
 *
 * <h2>AI에게 맡기는 것과 맡기지 않는 것</h2>
 * AI는 <b>매핑만 제안</b>한다. 값을 만들거나 고치지 않고, 확정은 사람이 한다.
 * 보내는 것도 <b>헤더와 샘플 3행</b>뿐이다 — 전체 데이터를 넘길 이유가 없고(비용·프라이버시),
 * 열 이름을 맞히는 데 그 이상이 필요하지도 않다.
 *
 * <p>AI가 없거나 실패해도 기능이 죽지 않는다. 이름만 보고 맞히는 <b>규칙 기반 추측</b>이
 * 먼저 돌고, AI는 그것이 못 채운 칸을 메우는 보조다.
 */
@Service
public class CsvColumnMapper {
  private static final Logger log = LoggerFactory.getLogger(CsvColumnMapper.class);

  /** 임포트가 기대하는 열 순서. {@code InventoryImportService.toForm}과 같아야 한다. */
  public static final List<String> TARGET_COLUMNS = List.of(
      "이름", "유형", "카테고리", "시리얼", "만료일", "구매일", "보증만료", "리스만료", "비고");

  /** 이름만 보고 맞히는 규칙. AI 없이도 대부분은 여기서 잡힌다. */
  private static final Map<String, List<String>> HINTS = Map.of(
      "이름", List.of("이름", "품명", "자산명", "name", "item", "asset", "모델", "제품"),
      "유형", List.of("유형", "종류", "구분", "type", "분류구분"),
      "카테고리", List.of("카테고리", "분류", "category", "그룹"),
      "시리얼", List.of("시리얼", "자산번호", "관리번호", "태그", "serial", "sn", "s/n", "asset no",
          "assetno", "barcode"),
      "만료일", List.of("만료", "expire", "expiry", "유효기간", "종료일"),
      "구매일", List.of("구매", "구입", "purchase", "취득", "도입일"),
      "보증만료", List.of("보증", "warranty", "as만료", "a/s"),
      "리스만료", List.of("리스", "렌탈", "임대", "lease", "rental"),
      "비고", List.of("비고", "메모", "note", "remark", "설명", "기타"));

  private final AiService aiService;

  public CsvColumnMapper(AiService aiService) {
    this.aiService = aiService;
  }

  /** 매핑 한 칸: 우리 열 ← 그쪽 열 인덱스(없으면 null). */
  public record Mapping(String target, Integer sourceIndex, String sourceHeader, String basis) {}

  /**
   * 헤더를 보고 매핑을 제안한다.
   *
   * @param headers 업로드한 CSV의 첫 줄
   * @param samples 샘플 행 몇 개(AI에 함께 보내 이름만으로 모호한 열을 구분하게 한다)
   */
  public List<Mapping> propose(
      UUID tenantId, List<String> headers, List<List<String>> samples) {
    Map<String, Integer> byRule = guessByName(headers);
    Map<String, Integer> byAi = byRule.size() == TARGET_COLUMNS.size()
        ? Map.of() // 규칙으로 다 찼으면 AI를 부르지 않는다(비용·지연)
        : askAi(tenantId, headers, samples);

    // 규칙과 AI가 같은 열을 집을 수 있다. 한 열이 두 항목에 쓰이면 같은 값이 두 칸에
    // 들어가므로, 이미 쓴 열은 뒤에서 다시 쓰지 않는다(규칙이 우선).
    java.util.Set<Integer> taken = new java.util.HashSet<>(byRule.values());
    List<Mapping> out = new ArrayList<>();
    for (String target : TARGET_COLUMNS) {
      Integer index = byRule.get(target);
      String basis = index == null ? null : "이름";
      if (index == null) {
        Integer suggested = byAi.get(target);
        if (suggested != null && !taken.contains(suggested)) {
          index = suggested;
          basis = "AI";
          taken.add(suggested);
        }
      }
      if (index != null && (index < 0 || index >= headers.size())) {
        index = null;
        basis = null;
      }
      out.add(new Mapping(target, index,
          index == null ? null : headers.get(index), basis));
    }
    return out;
  }

  /**
   * 확정된 매핑대로 CSV를 우리 열 순서로 재배열한다. 헤더 행은 버린다(임포트가 값만 읽는다).
   * <b>값은 손대지 않는다</b> — 자리만 옮긴다.
   */
  public String rearrange(List<List<String>> rows, Map<String, Integer> mapping) {
    StringBuilder out = new StringBuilder();
    for (List<String> row : rows) {
      List<String> line = new ArrayList<>();
      for (String target : TARGET_COLUMNS) {
        Integer index = mapping.get(target);
        String value = index == null || index < 0 || index >= row.size() ? "" : row.get(index);
        line.add(escape(value));
      }
      out.append(String.join(",", line)).append('\n');
    }
    return out.toString();
  }

  private static String escape(String value) {
    String v = value == null ? "" : value.trim();
    if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
      return '"' + v.replace("\"", "\"\"") + '"';
    }
    return v;
  }

  /** 헤더 이름에 힌트가 들어 있으면 그 열로 본다. 같은 열이 두 번 쓰이지 않게 한다. */
  private Map<String, Integer> guessByName(List<String> headers) {
    Map<String, Integer> out = new LinkedHashMap<>();
    List<Boolean> used = new ArrayList<>(headers.stream().map(h -> false).toList());
    for (String target : TARGET_COLUMNS) {
      for (int i = 0; i < headers.size(); i++) {
        if (used.get(i)) {
          continue;
        }
        String header = headers.get(i) == null ? "" : headers.get(i).toLowerCase().replace(" ", "");
        boolean hit = HINTS.getOrDefault(target, List.of()).stream()
            .anyMatch(hint -> header.contains(hint.toLowerCase().replace(" ", "")));
        if (hit) {
          out.put(target, i);
          used.set(i, true);
          break;
        }
      }
    }
    return out;
  }

  /**
   * AI에게 남은 열을 물어본다. 실패하면 <b>조용히 빈 결과</b>를 돌려준다 — 보조 기능이
   * 실패했다고 임포트 자체를 막을 이유가 없다(규칙 기반 추측과 사용자 수정이 남아 있다).
   */
  private Map<String, Integer> askAi(
      UUID tenantId, List<String> headers, List<List<String>> samples) {
    if (!aiService.isConfigured(tenantId)) {
      return Map.of();
    }
    try {
      String answer = aiService.generate(tenantId, prompt(headers, samples));
      return parse(answer, headers.size());
    } catch (RuntimeException failure) {
      log.debug("CSV 열 매핑 AI 제안 실패 — 규칙 기반 추측만 사용합니다.", failure);
      return Map.of();
    }
  }

  /** 헤더 + 샘플 3행만 싣는다. 전체 데이터를 넘길 이유가 없다(비용·프라이버시). */
  private String prompt(List<String> headers, List<List<String>> samples) {
    StringBuilder p = new StringBuilder();
    p.append("자산 대장 CSV의 열을 정해진 항목에 연결해줘.\n\n");
    p.append("우리 항목: ").append(String.join(", ", TARGET_COLUMNS)).append('\n');
    p.append("CSV 헤더(0부터 번호):\n");
    for (int i = 0; i < headers.size(); i++) {
      p.append("  ").append(i).append(": ").append(headers.get(i)).append('\n');
    }
    if (!samples.isEmpty()) {
      p.append("샘플 행:\n");
      for (List<String> row : samples.stream().limit(3).toList()) {
        p.append("  ").append(String.join(" | ", row)).append('\n');
      }
    }
    p.append("\n한 줄에 하나씩 `항목=번호` 형식으로만 답해줘. ");
    p.append("해당하는 열이 없으면 그 항목은 아예 쓰지 마. 설명은 붙이지 마.");
    return p.toString();
  }

  /**
   * {@code 항목=번호} 줄만 골라 읽는다. AI가 설명을 덧붙이거나 형식을 어겨도 파싱이 깨지지
   * 않아야 한다 — 우리 항목 이름과 범위 안의 번호만 받아들이고 나머지는 버린다.
   */
  private Map<String, Integer> parse(String answer, int columnCount) {
    Map<String, Integer> out = new LinkedHashMap<>();
    if (answer == null) {
      return out;
    }
    Pattern line = Pattern.compile("([가-힣A-Za-z]+)\\s*=\\s*(\\d+)");
    Matcher matcher = line.matcher(answer);
    while (matcher.find()) {
      String target = matcher.group(1);
      if (!TARGET_COLUMNS.contains(target) || out.containsKey(target)) {
        continue;
      }
      int index = Integer.parseInt(matcher.group(2));
      if (index >= 0 && index < columnCount && !out.containsValue(index)) {
        out.put(target, index);
      }
    }
    return out;
  }

  /** 화면이 쓰는 CSV 한 줄 파서(따옴표 안의 쉼표 보존). */
  public static List<String> parseLine(String line) {
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            quoted = false;
          }
        } else {
          field.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        fields.add(field.toString());
        field.setLength(0);
      } else {
        field.append(c);
      }
    }
    fields.add(field.toString());
    return fields;
  }

  /** 붙여넣기·업로드된 CSV를 행 목록으로. BOM과 빈 줄을 걸러낸다. */
  public static List<List<String>> parseRows(String csv) {
    List<List<String>> rows = new ArrayList<>();
    if (csv == null || csv.isBlank()) {
      return rows;
    }
    String text = csv.startsWith("﻿") ? csv.substring(1) : csv;
    for (String raw : text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1)) {
      if (!raw.isBlank()) {
        rows.add(parseLine(raw));
      }
    }
    return rows;
  }

  /** 첫 줄이 헤더처럼 보이는지(값이 아니라 이름처럼). 날짜·숫자만 있으면 데이터로 본다. */
  public static boolean looksLikeHeader(List<String> row) {
    return row.stream().anyMatch(cell -> {
      String c = cell == null ? "" : cell.trim();
      return !c.isEmpty() && !c.matches("\\d{4}-\\d{2}-\\d{2}") && !c.matches("-?\\d+(\\.\\d+)?");
    }) && row.stream().noneMatch(cell -> cell != null && cell.trim().matches("\\d{4}-\\d{2}-\\d{2}"));
  }

  /** 우리 열 이름 목록(화면 렌더용). */
  public static List<String> targets() {
    return new ArrayList<>(TARGET_COLUMNS);
  }

  static {
    // HINTS가 TARGET_COLUMNS를 모두 덮는지 기동 시점에 확인한다 — 열을 추가하고 힌트를
    // 빠뜨리면 그 열은 영영 자동 매핑되지 않는다.
    List<String> missing = TARGET_COLUMNS.stream().filter(t -> !HINTS.containsKey(t)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("CSV 매핑 힌트가 빠진 열: " + Arrays.toString(missing.toArray()));
    }
  }
}
