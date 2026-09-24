package com.moara.moa.consumable;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 주문 주기 예측. 이 계산이 소모품 기능의 전부라, 화면·알림보다 먼저 못 박는다.
 *
 * <p>스프링 없이 순수 계산만 본다 — 규칙이 맞는지 확인하는 데 DB가 필요 없고,
 * 없어야 경계(표본 부족·불규칙·같은 날 주문)를 빠르게 훑을 수 있다.
 */
class ConsumableForecastTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 3, 20);

  private static List<ConsumableOrder> orders(Object... dateQtyPairs) {
    List<ConsumableOrder> out = new ArrayList<>();
    for (int i = 0; i < dateQtyPairs.length; i += 2) {
      out.add(new ConsumableOrder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
          LocalDate.parse((String) dateQtyPairs[i]), (Integer) dateQtyPairs[i + 1], null, null,
          OffsetDateTime.now()));
    }
    return out;
  }

  /**
   * 요청하신 예시 그대로: 30·20·25일 간격에 매번 1박스. 평균 25일이 나와야 한다.
   * 수량이 모두 같으면 이 계산은 '간격 방식'과 정확히 같은 답을 낸다 — 일반화가 맞다는 확인.
   */
  @Test
  void 간격_30_20_25에_매번_1박스면_다음은_25일_뒤다() {
    var f = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-01-31", 1, "2026-02-20", 1, "2026-03-17", 1), null, TODAY);

    assertThat(f.basis()).isEqualTo(ConsumableForecast.Basis.MEASURED);
    assertThat(f.cycleDays()).isEqualTo(25);
    assertThat(f.dueOn()).isEqualTo(LocalDate.of(2026, 3, 17).plusDays(25));
    assertThat(f.intervals()).containsExactly(30L, 20L, 25L);
  }

  /**
   * 마지막에 4박스를 사면 그만큼 오래 간다. 간격만 보는 방식이 틀리는 지점이고,
   * 수량을 받는 이유 그 자체다.
   */
  @Test
  void 마지막에_많이_사면_다음_주문이_그만큼_미뤄진다() {
    var f = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-01-31", 1, "2026-02-20", 1, "2026-03-17", 4), null, TODAY);

    // 소비율 중앙값 0.04/일 → 4 ÷ 0.04 = 100일
    assertThat(f.cycleDays()).isEqualTo(100);
    assertThat(f.dueOn()).isEqualTo(LocalDate.of(2026, 3, 17).plusDays(100));
  }

  /** 수량을 안 넣으면(전부 1) 간격 방식으로 자연스럽게 강등된다 — 입력이 부실해도 죽지 않는다. */
  @Test
  void 수량이_전부_1이면_간격_방식과_같다() {
    var withQty = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-02-01", 1, "2026-03-01", 1), null, TODAY);

    assertThat(withQty.cycleDays()).isBetween(28, 31);
  }

  /** 표본이 하나뿐(주문 2회)이면 예측하지 않는다 — 근거 없는 숫자를 통지하지 않는다. */
  @Test
  void 주문이_두_번뿐이면_실측_예측을_하지_않는다() {
    var f = ConsumableForecast.of(orders("2026-02-01", 1, "2026-03-01", 1), null, TODAY);

    assertThat(f.basis()).isEqualTo(ConsumableForecast.Basis.NONE);
    assertThat(f.dueOn()).isNull();
    assertThat(f.isDue(TODAY)).isFalse();
  }

  /**
   * 콜드스타트: 실측이 부족해도 담당자가 넣은 주기가 있으면 그것으로 알린다.
   * 없으면 신규 품목은 주문 3회가 쌓일 때까지(분기 품목이면 9개월) 무반응이다.
   */
  @Test
  void 실측이_부족하면_담당자가_넣은_주기를_쓴다() {
    var f = ConsumableForecast.of(orders("2026-03-01", 1), 30, TODAY);

    assertThat(f.basis()).isEqualTo(ConsumableForecast.Basis.CONFIGURED);
    assertThat(f.cycleDays()).isEqualTo(30);
    assertThat(f.dueOn()).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  /** 주문이 아예 없어도 설정값이 있으면 오늘 기준으로 잡는다(등록 직후에도 동작). */
  @Test
  void 주문이_없어도_설정값이_있으면_예측한다() {
    var f = ConsumableForecast.of(List.of(), 14, TODAY);

    assertThat(f.basis()).isEqualTo(ConsumableForecast.Basis.CONFIGURED);
    assertThat(f.dueOn()).isEqualTo(TODAY.plusDays(14));
  }

  /** 근거가 하나도 없으면 아무 말도 하지 않는다. */
  @Test
  void 근거가_없으면_침묵한다() {
    var f = ConsumableForecast.of(List.of(), null, TODAY);

    assertThat(f.basis()).isEqualTo(ConsumableForecast.Basis.NONE);
    assertThat(f.isDue(TODAY)).isFalse();
  }

  /**
   * 들쭉날쭉해도 <b>침묵하지 않는다</b>. 리마인더이므로 틀려도 비용이 낮고, 확신의 정도만
   * 낮춰 알린다(문구는 화면·알림이 basis를 보고 정한다).
   */
  @Test
  void 간격이_들쭉날쭉하면_알리되_확신하지_않는다() {
    var f = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-01-11", 1, "2026-02-20", 1, "2026-03-15", 1), null, TODAY);

    assertThat(f.basis()).isEqualTo(ConsumableForecast.Basis.IRREGULAR);
    assertThat(f.dueOn()).isNotNull();
  }

  /** 한 번의 예외가 답을 끌고 가지 않는다 — 평균이 아니라 중앙값을 쓰는 이유. */
  @Test
  void 예외_구간_하나가_결과를_끌고_가지_않는다() {
    var normal = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-01-31", 1, "2026-03-01", 1, "2026-03-31", 1),
        null, LocalDate.of(2026, 4, 1));
    var withOutlier = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-01-31", 1, "2026-03-01", 1, "2026-03-31", 1,
               "2026-04-02", 1),
        null, LocalDate.of(2026, 4, 3));

    // 마지막에 이틀 만에 한 번 더 사도 주기가 2일로 무너지지 않는다.
    assertThat(withOutlier.cycleDays()).isGreaterThan(10);
    assertThat(normal.cycleDays()).isBetween(28, 31);
  }

  /** 같은 날 두 번 주문하면 한 건으로 합친다 — 간격 0은 소비율을 무한대로 만든다. */
  @Test
  void 같은_날_주문은_한_건으로_합친다() {
    var f = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-01-31", 1, "2026-02-20", 1, "2026-02-20", 2,
               "2026-03-17", 1), null, TODAY);

    assertThat(f.cycleDays()).isNotNull();
    assertThat(f.intervals()).doesNotContain(0L);
  }

  /** 드물게 사는 품목도 예측이 나온다 — "3개월"을 기간으로만 읽으면 표본이 영영 안 모인다. */
  @Test
  void 분기마다_사는_품목도_최근_주문으로_예측한다() {
    var f = ConsumableForecast.of(
        orders("2025-06-01", 1, "2025-09-01", 1, "2025-12-01", 1, "2026-03-01", 1),
        null, TODAY);

    assertThat(f.basis()).isEqualTo(ConsumableForecast.Basis.MEASURED);
    assertThat(f.cycleDays()).isBetween(85, 95);
  }

  /** 실측이 설정값과 크게 어긋나면 보정을 제안한다(몰래 바꾸지는 않는다). */
  @Test
  void 실측이_설정값과_크게_다르면_보정을_제안한다() {
    var f = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-01-19", 1, "2026-02-06", 1, "2026-02-24", 1), 30, TODAY);

    assertThat(f.cycleDays()).isEqualTo(18);
    assertThat(f.suggestsCycleChange(30)).isTrue();
    assertThat(f.suggestsCycleChange(18)).isFalse();
  }

  @Test
  void 예상일이_지나면_알림_대상이다() {
    var f = ConsumableForecast.of(
        orders("2026-01-01", 1, "2026-01-31", 1, "2026-02-20", 1, "2026-03-17", 1), null, TODAY);

    assertThat(f.isDue(LocalDate.of(2026, 4, 10))).isFalse();
    assertThat(f.isDue(LocalDate.of(2026, 4, 11))).isTrue();
  }
}
