package com.moara.moa.audit;

import com.moara.moa.ai.AiService;
import com.moara.moa.user.ManagedUserService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 어제 감사 기록의 <b>이상 징후 요약</b>. PAM 제품에서 AI가 가장 값어치를 내는 자리다 —
 * 데이터는 이미 다 쌓여 있는데 아무도 읽지 않는다.
 *
 * <h2>AI가 하는 일과 하지 않는 일</h2>
 * <b>무엇이 이상한지는 코드가 판정한다.</b> 평소 대비 비교, 야간 접속 판별, 위험 행위 목록은
 * 전부 여기서 계산하고, AI는 그 결과를 <b>사람이 읽는 문장으로 바꿀 뿐</b>이다.
 * AI에게 판정을 맡기면 왜 그렇게 판단했는지 설명할 수 없고, 매번 답이 달라져 신뢰를 잃는다.
 *
 * <h2>원문 로그를 보내지 않는다</h2>
 * 건수·대상·시간대로 <b>집계한 결과</b>만 프롬프트에 싣는다. 비용도 비용이지만, 감사 로그는
 * 누가 언제 무엇을 했는지가 그대로 들어 있어 통째로 외부에 보낼 성질이 아니다.
 *
 * <p>AI가 없으면 집계 결과를 그대로 보여 준다 — 요약이 없을 뿐 기능이 사라지지 않는다.
 */
@Service
public class AuditDigestService {
  private static final Logger log = LoggerFactory.getLogger(AuditDigestService.class);

  /** 기준 시간대. 만료·소모품 배치와 같은 '하루'를 써야 설명이 어긋나지 않는다. */
  public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

  /** 밤에 일어나면 한 번 더 보게 되는 시간대(포함~미만). */
  private static final int NIGHT_FROM = 22;
  private static final int NIGHT_TO = 6;

  /**
   * 한 건만 있어도 관리자가 알아야 하는 행위. <b>무엇이 위험한지는 코드가 정한다</b> —
   * AI에게 물으면 매번 답이 달라진다.
   */
  private static final Set<String> SENSITIVE_ACTIONS = Set.of(
      "USER_TWO_FACTOR_DISABLE", "TWO_FACTOR_DISABLE", "USER_OFFBOARD", "USER_DISABLE",
      "CREDENTIAL_REVEAL", "CREDENTIAL_DELETE", "PERMISSION_DELETE", "WIKI_SPACE_DELETE",
      "MAIL_SETTING_UPDATE", "AI_SETTING_UPDATE", "TENANT_ADMIN_RESET");

  private final AuditLogRepository repository;
  private final ManagedUserService userService;
  private final AiService aiService;

  public AuditDigestService(
      AuditLogRepository repository, ManagedUserService userService, AiService aiService) {
    this.repository = repository;
    this.userService = userService;
    this.aiService = aiService;
  }

  /**
   * 하루치 요약 결과.
   *
   * @param facts 코드가 계산한 사실들(화면에 그대로 보여 준다)
   * @param summary AI가 문장으로 바꾼 것. AI가 없거나 실패하면 null
   */
  public record Digest(LocalDate on, int total, List<String> facts, String summary) {}

  /** 어제(기준 시간대) 기록을 요약한다. */
  public Digest yesterday(UUID tenantId) {
    return forDate(tenantId, LocalDate.now(ZONE).minusDays(1));
  }

  public Digest forDate(UUID tenantId, LocalDate date) {
    OffsetDateTime from = date.atStartOfDay(ZONE).toOffsetDateTime();
    OffsetDateTime to = date.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();
    List<AuditLog> logs = repository
        .findAllByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            tenantId, from, to);
    if (logs.isEmpty()) {
      return new Digest(date, 0, List.of(), null);
    }
    List<String> facts = facts(tenantId, logs, date);
    return new Digest(date, logs.size(), facts, summarize(tenantId, date, logs.size(), facts));
  }

  /**
   * 코드가 판정하는 사실들. 여기서 나오지 않은 것은 AI도 말하지 않는다 —
   * 프롬프트에 이 목록만 싣기 때문이다.
   */
  private List<String> facts(UUID tenantId, List<AuditLog> logs, LocalDate date) {
    Map<UUID, String> names = userService.namesByTenant(tenantId);
    List<String> facts = new ArrayList<>();

    long failures = logs.stream().filter(l -> l.getResult() == AuditResult.FAILURE).count();
    if (failures > 0) {
      facts.add("실패한 작업 " + failures + "건");
      Map<String, Long> byActor = logs.stream()
          .filter(l -> l.getResult() == AuditResult.FAILURE)
          .collect(Collectors.groupingBy(l -> actor(names, l), Collectors.counting()));
      byActor.entrySet().stream()
          .filter(e -> e.getValue() >= 3)
          .forEach(e -> facts.add("실패가 한 사람(" + e.getKey() + ")에게 " + e.getValue() + "건 몰림"));
    }

    logs.stream()
        .filter(l -> SENSITIVE_ACTIONS.contains(l.getAction()))
        .collect(Collectors.groupingBy(AuditLog::getAction, LinkedHashMap::new, Collectors.toList()))
        .forEach((action, group) -> facts.add(
            "민감 행위 " + action + " " + group.size() + "건 (" + actorList(names, group) + ")"));

    long night = logs.stream().filter(this::isNight).count();
    if (night > 0) {
      facts.add("야간(22시~06시) 작업 " + night + "건");
    }

    Map<String, Long> topActors = logs.stream()
        .collect(Collectors.groupingBy(l -> actor(names, l), Collectors.counting()));
    topActors.entrySet().stream()
        .max(Comparator.comparingLong(Map.Entry::getValue))
        .filter(e -> e.getValue() >= 10)
        .ifPresent(e -> facts.add("가장 많이 움직인 사람: " + e.getKey() + " " + e.getValue() + "건"));

    if (facts.isEmpty()) {
      facts.add("특이 사항 없음 — 실패·민감 행위·야간 작업이 없었습니다.");
    }
    return facts;
  }

  private boolean isNight(AuditLog log) {
    int hour = log.getCreatedAt().atZoneSameInstant(ZONE).getHour();
    return hour >= NIGHT_FROM || hour < NIGHT_TO;
  }

  private String actor(Map<UUID, String> names, AuditLog log) {
    return log.getActorUserId() == null ? "시스템"
        : names.getOrDefault(log.getActorUserId(), "알 수 없음");
  }

  private String actorList(Map<UUID, String> names, List<AuditLog> logs) {
    return logs.stream().map(l -> actor(names, l)).distinct().limit(3)
        .collect(Collectors.joining(", "));
  }

  /**
   * 집계 결과를 문장으로. <b>사실 목록만 보내고</b> 원문 로그는 보내지 않는다.
   * 실패하면 null을 돌려주고 화면은 사실 목록만 보여 준다 — 요약이 없을 뿐이다.
   */
  private String summarize(UUID tenantId, LocalDate date, int total, List<String> facts) {
    if (!aiService.isConfigured(tenantId)) {
      return null;
    }
    String prompt = "사내 시스템의 " + date + " 감사 기록 집계다. 관리자가 아침에 읽을 3줄 이내"
        + " 한국어 요약을 써줘. 아래 사실만 근거로 쓰고 없는 내용은 지어내지 마."
        + " 숫자는 그대로 인용해. 조치를 지시하지 말고 상황만 전해줘.\n\n"
        + "총 기록 " + total + "건\n"
        + facts.stream().map(f -> "- " + f).collect(Collectors.joining("\n"));
    try {
      return aiService.generate(tenantId, prompt);
    } catch (RuntimeException failure) {  // AiException 포함
      log.debug("감사 요약 생성 실패 — 집계 결과만 보여 줍니다.", failure);
      return null;
    }
  }
}
