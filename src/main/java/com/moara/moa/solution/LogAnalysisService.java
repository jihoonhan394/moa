package com.moara.moa.solution;

import com.moara.moa.ai.AiService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * 솔루션 로그 <b>온디맨드</b> 분석(실시간 아님). 비용을 아끼기 위해 <b>먼저 로컬에서 걸러</b> 극히 일부만 AI로 보낸다:
 * (1) 마지막 최대 {@value #MAX_LINES}줄만, (2) 크리티컬 오류 라인만 추출(+스택트레이스), (3) 중복 오류는 ×N으로 접음,
 * (4) <b>오류가 0건이면 AI를 호출하지 않는다</b>(정상 로그엔 0토큰), (5) AI 호출 시에도 고유 오류 상위 {@value #MAX_GROUPS}개로 상한.
 * 상용 로그분석(Datadog·Coralogix 등)이 원본을 LLM에 안 넣고 선필터·패턴화하는 방식과 같은 원리.
 */
@Service
public class LogAnalysisService {
  static final int MAX_LINES = 1000;
  static final int MAX_GROUPS = 25;
  static final int MAX_STACK = 15;
  static final int SAMPLE_CHARS = 500;

  // 크리티컬 신호(대소문자 무시). "failed"처럼 과다 노이즈 단어는 제외하고 실제 장애 신호만.
  private static final Pattern CRITICAL = Pattern.compile(
      "(?i)(\\bERROR\\b|\\bFATAL\\b|\\bSEVERE\\b|\\bCRITICAL\\b|Exception|Traceback|panic:"
          + "|Out ?Of ?Memory|\\bOOM\\b|Caused by|segfault|core dumped|StackOverflow|Unhandled)");
  private static final Pattern LEADING_TS = Pattern.compile("^[\\d\\-:/T .,\\[\\]]+");

  private final SolutionControlService controlService;
  private final AiService aiService;

  public LogAnalysisService(SolutionControlService controlService, AiService aiService) {
    this.controlService = controlService;
    this.aiService = aiService;
  }

  /** 중복 접힌 오류 한 건(대표 샘플 + 발생 횟수). */
  public record ErrorGroup(String sample, int count) {}

  /** 분석 결과. aiSummary=null이면 AI 미호출(오류 0건 또는 AI 미설정) — note로 사유 전달. */
  public record LogAnalysis(List<ErrorGroup> groups, String aiSummary, boolean aiUsed, String note) {}

  /** 원격에서 로그를 가져와 오류를 추출하고, 오류가 있을 때만 AI로 요약·원인·조치를 만든다. */
  public LogAnalysis analyze(UUID tenantId, UUID solutionId) {
    String raw = controlService.fetchLogRaw(tenantId, solutionId);
    List<ErrorGroup> groups = digestErrors(raw);
    if (groups.isEmpty()) {
      return new LogAnalysis(List.of(), null, false, "크리티컬 오류가 발견되지 않았습니다. (AI 호출 안 함)");
    }
    if (!aiService.isConfigured(tenantId)) {
      return new LogAnalysis(groups, null, false, "AI 미설정 — 추출된 오류만 표시합니다.");
    }
    String summary = aiService.generate(tenantId, buildPrompt(groups));
    return new LogAnalysis(groups, summary, true, null);
  }

  /**
   * 원시 로그에서 크리티컬 오류를 추출·중복 접기한다(순수 함수 — AI/원격 없이 단독 테스트 가능).
   * 마지막 {@value #MAX_LINES}줄만 보고, 오류 라인에 이어지는 스택트레이스를 한 블록으로 묶어 지문으로 중복을 센다.
   */
  public List<ErrorGroup> digestErrors(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String[] lines = raw.split("\r?\n");
    int start = Math.max(0, lines.length - MAX_LINES);
    // 지문 → [샘플, 횟수]. 삽입 순서 유지.
    Map<String, int[]> counts = new LinkedHashMap<>();
    Map<String, String> samples = new LinkedHashMap<>();
    int i = start;
    while (i < lines.length) {
      String line = lines[i];
      if (line != null && CRITICAL.matcher(line).find()) {
        StringBuilder block = new StringBuilder(line.strip());
        int j = i + 1;
        int extra = 0;
        while (j < lines.length && extra < MAX_STACK && isContinuation(lines[j])) {
          block.append('\n').append(lines[j].strip());
          j++;
          extra++;
        }
        String fp = fingerprint(line);
        if (counts.containsKey(fp)) {
          counts.get(fp)[0]++;
        } else if (counts.size() < MAX_GROUPS) {
          counts.put(fp, new int[] {1});
          samples.put(fp, truncate(block.toString()));
        }
        i = j;
      } else {
        i++;
      }
    }
    List<ErrorGroup> out = new ArrayList<>();
    for (Map.Entry<String, int[]> e : counts.entrySet()) {
      out.add(new ErrorGroup(samples.get(e.getKey()), e.getValue()[0]));
    }
    return out;
  }

  private boolean isContinuation(String line) {
    if (line == null || line.isBlank()) {
      return false;
    }
    if (Character.isWhitespace(line.charAt(0))) {
      return true; // 들여쓴 스택 프레임
    }
    String s = line.strip();
    return s.startsWith("at ") || s.startsWith("Caused by") || s.startsWith("...") || s.startsWith("\tat ");
  }

  /** 지문: 선두 타임스탬프 제거 + 숫자/16진수 일반화 → 반복 오류가 같은 키로 묶이게. */
  private String fingerprint(String line) {
    String s = LEADING_TS.matcher(line.strip()).replaceFirst("");
    s = s.replaceAll("0x[0-9a-fA-F]+", "#").replaceAll("\\d+", "#");
    return s.toLowerCase().replaceAll("\\s+", " ").trim();
  }

  private String truncate(String s) {
    return s.length() <= SAMPLE_CHARS ? s : s.substring(0, SAMPLE_CHARS) + " …";
  }

  private String buildPrompt(List<ErrorGroup> groups) {
    StringBuilder p = new StringBuilder();
    p.append("당신은 시스템 운영 담당자를 돕는 어시스턴트입니다. 아래는 솔루션 로그에서 추출한 ")
        .append("크리티컬 오류들이며, 중복은 ×N(발생 횟수)으로 접었습니다. 각 오류에 대해 한국어로 간결히:\n")
        .append("1) 무슨 오류인지 2) 심각도 3) 가능한 원인 4) 확인·조치. 로그에 없는 내용은 지어내지 마세요.\n\n");
    int n = 1;
    for (ErrorGroup g : groups) {
      p.append("== 오류 ").append(n++).append(" (×").append(g.count()).append(") ==\n")
          .append(g.sample()).append("\n\n");
    }
    return p.toString();
  }
}
