package com.moara.moa.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moara.moa.ai.AiException;
import com.moara.moa.ai.AiService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 남의 엑셀 열을 우리 항목에 연결하는 매퍼.
 *
 * <p>검증의 초점은 <b>AI가 없거나 이상하게 답해도 망가지지 않는가</b>다. 이건 보조 기능이라
 * 실패했다고 임포트 자체를 막아선 안 되고, AI 출력은 언제든 형식을 어길 수 있다.
 */
class CsvColumnMapperTest {
  private static final UUID TENANT = UUID.randomUUID();

  private final AiService ai = mock(AiService.class);
  private final CsvColumnMapper mapper = new CsvColumnMapper(ai);

  private static Map<String, Integer> indexes(List<CsvColumnMapper.Mapping> mappings) {
    return mappings.stream()
        .filter(m -> m.sourceIndex() != null)
        .collect(java.util.stream.Collectors.toMap(
            CsvColumnMapper.Mapping::target, CsvColumnMapper.Mapping::sourceIndex));
  }

  /** 이름만 봐도 알 수 있으면 AI를 부르지 않는다 — 부를 이유가 없는데 부르면 비용·지연만 는다. */
  @Test
  void 열_이름으로_다_맞히면_AI를_부르지_않는다() {
    List<String> headers = List.of(
        "이름", "유형", "카테고리", "시리얼", "만료일", "구매일", "보증만료", "리스만료", "비고");

    var mappings = mapper.propose(TENANT, headers, List.of());

    assertThat(indexes(mappings)).containsEntry("이름", 0).containsEntry("비고", 8);
    assertThat(mappings).allMatch(m -> m.basis() == null || m.basis().equals("이름"));
    verify(ai, never()).generate(any(), anyString());
  }

  /** 기관마다 다른 이름도 규칙으로 잡는다(자산번호·관리번호·태그 → 시리얼). */
  @Test
  void 기관마다_다른_이름도_규칙으로_잡는다() {
    when(ai.isConfigured(TENANT)).thenReturn(false);

    var byAssetNo = indexes(mapper.propose(TENANT, List.of("품명", "관리번호", "구입일"), List.of()));
    assertThat(byAssetNo).containsEntry("이름", 0).containsEntry("시리얼", 1)
        .containsEntry("구매일", 2);

    var english = indexes(mapper.propose(TENANT, List.of("Asset Name", "S/N", "Purchase Date"), List.of()));
    assertThat(english).containsEntry("이름", 0).containsEntry("시리얼", 1).containsEntry("구매일", 2);
  }

  /** 규칙이 못 채운 칸만 AI에게 묻는다. */
  @Test
  void 규칙이_못_채운_칸을_AI가_메운다() {
    when(ai.isConfigured(TENANT)).thenReturn(true);
    when(ai.generate(any(), anyString())).thenReturn("비고=1");

    var mappings = mapper.propose(TENANT, List.of("이름", "참고사항"), List.of());

    assertThat(indexes(mappings)).containsEntry("이름", 0).containsEntry("비고", 1);
    assertThat(mappings).anyMatch(m -> "AI".equals(m.basis()) && m.target().equals("비고"));
  }

  /** AI가 설정 안 됐으면 규칙 결과만 쓴다 — 기능이 사라지지 않는다. */
  @Test
  void AI가_없어도_규칙_결과는_나온다() {
    when(ai.isConfigured(TENANT)).thenReturn(false);

    var mappings = mapper.propose(TENANT, List.of("품명", "알수없는열"), List.of());

    assertThat(indexes(mappings)).containsEntry("이름", 0);
    verify(ai, never()).generate(any(), anyString());
  }

  /** AI 호출이 터져도 임포트를 막지 않는다. */
  @Test
  void AI가_실패해도_규칙_결과로_계속한다() {
    when(ai.isConfigured(TENANT)).thenReturn(true);
    when(ai.generate(any(), anyString())).thenThrow(new AiException("연결 실패"));

    var mappings = mapper.propose(TENANT, List.of("품명", "알수없는열"), List.of());

    assertThat(indexes(mappings)).containsEntry("이름", 0);
  }

  /**
   * AI가 형식을 어기거나 없는 항목·범위 밖 번호를 말해도 버린다. 출력이 언제나 얌전하다고
   * 가정하면 조용히 엉뚱한 열이 들어간다.
   */
  @Test
  void AI의_엉뚱한_답은_버린다() {
    when(ai.isConfigured(TENANT)).thenReturn(true);
    when(ai.generate(any(), anyString())).thenReturn(
        "물론이죠! 분석해 보니:\n가격=1\n비고=99\n시리얼=1\n이름=0\n(도움이 되었길 바랍니다)");

    var mappings = mapper.propose(TENANT, List.of("이름", "일련번호"), List.of());
    var idx = indexes(mappings);

    assertThat(idx).doesNotContainKey("가격");   // 우리 항목이 아니다
    assertThat(idx).doesNotContainKey("비고");   // 범위 밖 번호
    assertThat(idx).containsEntry("시리얼", 1);
  }

  /** 한 열이 두 항목에 겹쳐 쓰이지 않는다 — 겹치면 같은 값이 두 칸에 들어간다. */
  @Test
  void 한_열을_두_항목에_쓰지_않는다() {
    when(ai.isConfigured(TENANT)).thenReturn(true);
    when(ai.generate(any(), anyString())).thenReturn("비고=0\n카테고리=0");

    var idx = indexes(mapper.propose(TENANT, List.of("이름"), List.of()));

    assertThat(idx.values()).doesNotHaveDuplicates();
  }

  /** 재배열은 자리만 옮긴다 — 값을 고치지 않는다. */
  @Test
  void 재배열은_값을_그대로_옮긴다() {
    List<List<String>> rows = List.of(
        List.of("SN-1", "노트북", "2026-01-01"),
        List.of("SN-2", "모니터", "2026-02-02"));

    String csv = mapper.rearrange(rows, Map.of("이름", 1, "시리얼", 0, "구매일", 2));

    String[] lines = csv.strip().split("\n");
    assertThat(lines).hasSize(2);
    // 순서: 이름,유형,카테고리,시리얼,만료일,구매일,...
    assertThat(lines[0]).startsWith("노트북,,,SN-1,,2026-01-01");
    assertThat(lines[1]).startsWith("모니터,,,SN-2,,2026-02-02");
  }

  /** 쉼표·따옴표가 든 값도 깨지지 않게 감싼다. */
  @Test
  void 쉼표가_든_값은_따옴표로_감싼다() {
    String csv = mapper.rearrange(List.of(List.of("모니터, 27인치")), Map.of("이름", 0));

    assertThat(csv.strip()).startsWith("\"모니터, 27인치\",");
  }

  /** 연결하지 않은 항목은 빈 칸으로 남는다(임포트가 선택 항목으로 처리한다). */
  @Test
  void 연결하지_않은_항목은_빈_칸이_된다() {
    String csv = mapper.rearrange(List.of(List.of("노트북")), Map.of("이름", 0));

    assertThat(csv.strip()).isEqualTo("노트북,,,,,,,,");
  }

  @Test
  void 헤더인지_데이터인지_구분한다() {
    assertThat(CsvColumnMapper.looksLikeHeader(List.of("이름", "유형", "구매일"))).isTrue();
    assertThat(CsvColumnMapper.looksLikeHeader(List.of("노트북", "실물", "2026-01-01"))).isFalse();
  }

  @Test
  void 따옴표_안의_쉼표를_보존한다() {
    assertThat(CsvColumnMapper.parseLine("\"모니터, 27인치\",실물,메모"))
        .containsExactly("모니터, 27인치", "실물", "메모");
  }
}
