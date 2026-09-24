package com.moara.moa.consumable;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 주문 주기 예측. <b>"언제쯤 또 살 때가 됐나"</b>를 주문 이력만으로 추정한다.
 *
 * <h2>왜 소비량을 추적하지 않나</h2>
 * 한 박스 꺼낼 때마다 입력하게 하면 아무도 안 한다 — 재고관리 시스템이 실패하는 1번 이유다.
 * 반면 주문은 돈이 나가고 결재·영수증이 붙어 기록될 가능성이 높다. <b>없는 데이터로 하는
 * 정확한 계산보다 있는 데이터로 하는 대략적인 계산이 낫다.</b>
 *
 * <h2>수량이 있으면 소비율이 공짜로 나온다</h2>
 * 핵심 관찰: <b>한 번 주문한 수량은 다음 주문 때까지 소비된 양이다</b>(재고가 바닥날 즈음
 * 주문한다는 전제). 그래서 구간마다 {@code 주문량 ÷ 구간 길이}가 그 기간의 소비율이 된다.
 *
 * <pre>
 *   주문1 01-01 1박스 ──30일──┐
 *   주문2 01-31 1박스 ──20일──┤  소비율 = 1/30, 1/20, 1/25  →  중앙값 0.04박스/일
 *   주문3 02-20 1박스 ──25일──┘
 *   주문4 03-17 4박스          →  4 ÷ 0.04 = 100일 뒤가 다음 차례
 * </pre>
 *
 * 수량이 전부 같으면 결과가 <b>'간격 평균' 방식과 정확히 같아진다</b>(1 ÷ 0.04 = 25일).
 * 즉 이 계산은 간격 방식의 일반화이고, 수량이 다를 때만 자동으로 보정된다.
 * 수량을 안 넣으면 전부 1로 간주되어 저절로 간격 방식이 된다 — 입력이 부실해도 죽지 않는다.
 *
 * <h2>평균이 아니라 중앙값</h2>
 * 표본이 3~5개뿐이라 한 번의 예외(장기 휴가·이사·대량 특가)가 평균을 통째로 끌고 간다.
 * 중앙값은 그런 구간을 저절로 흘려보낸다.
 *
 * <h2>확신의 정도를 문구가 반영한다</h2>
 * 이건 발주 승인이 아니라 <b>리마인더</b>다. 틀려도 비용은 창고를 한 번 보는 것이라,
 * 표본이 들쭉날쭉해도 침묵하지 않고 <b>약한 문구로</b> 알린다. 근거 없는 숫자를 확신에 차서
 * 통지하는 것이 이 기능을 죽이는 가장 빠른 길이다.
 */
public final class ConsumableForecast {
  /** 표본을 모으는 기본 기간. 이 안에 간격이 부족하면 최근 주문 몇 건으로 넓힌다. */
  static final int WINDOW_DAYS = 90;
  /** 기간 안에 표본이 부족할 때 대신 볼 최근 주문 수(= 간격 3개). */
  static final int FALLBACK_ORDERS = 4;
  /** 이만큼 벌어지면 "주기"라고 부르기 어렵다 — 알리되 확신하지 않는다. */
  static final double IRREGULAR_RATIO = 3.0;

  /** 예측의 근거 수준. 화면·알림 문구가 이 값에 따라 달라진다. */
  public enum Basis {
    /** 실측 표본이 고르다. 근거 수치를 그대로 보여 준다. */
    MEASURED,
    /** 실측은 있으나 들쭉날쭉하다. 알리되 "일정하지 않지만"이라고 말한다. */
    IRREGULAR,
    /** 실측이 부족해 담당자가 넣은 예상 주기를 쓴다. */
    CONFIGURED,
    /** 근거가 없다. 아무 말도 하지 않는다. */
    NONE
  }

  private final Basis basis;
  private final Integer cycleDays;
  private final LocalDate dueOn;
  private final List<Long> intervals;
  private final Double dailyUsage;
  private final LocalDate lastOrderedOn;
  private final Integer lastQuantity;

  private ConsumableForecast(
      Basis basis, Integer cycleDays, LocalDate dueOn, List<Long> intervals, Double dailyUsage,
      LocalDate lastOrderedOn, Integer lastQuantity) {
    this.basis = basis;
    this.cycleDays = cycleDays;
    this.dueOn = dueOn;
    this.intervals = intervals;
    this.dailyUsage = dailyUsage;
    this.lastOrderedOn = lastOrderedOn;
    this.lastQuantity = lastQuantity;
  }

  /**
   * 주문 이력과 담당자 설정으로 다음 주문 시점을 추정한다.
   *
   * @param orders 이 품목의 주문 전체(순서 무관)
   * @param configuredCycleDays 담당자가 넣은 예상 주기. 실측이 부족할 때만 쓴다
   */
  public static ConsumableForecast of(
      List<ConsumableOrder> orders, Integer configuredCycleDays, LocalDate today) {
    List<ConsumableOrder> sorted = merged(orders);
    if (sorted.isEmpty()) {
      return configured(configuredCycleDays, null, today);
    }
    ConsumableOrder last = sorted.get(sorted.size() - 1);
    List<ConsumableOrder> sample = sample(sorted, today);
    List<Double> rates = new ArrayList<>();
    List<Long> gaps = new ArrayList<>();
    for (int i = 0; i + 1 < sample.size(); i++) {
      long gap = ChronoUnit.DAYS.between(sample.get(i).getOrderedOn(), sample.get(i + 1).getOrderedOn());
      if (gap <= 0) {
        continue;
      }
      gaps.add(gap);
      rates.add((double) sample.get(i).getQuantity() / gap);
    }
    if (rates.size() < 2) {
      return configured(configuredCycleDays, last, today);
    }
    double usage = median(rates);
    double spread = max(rates) / Math.max(min(rates), 1e-9);
    if (usage <= 0) {
      return configured(configuredCycleDays, last, today);
    }
    int remaining = (int) Math.max(1, Math.round(last.getQuantity() / usage));
    return new ConsumableForecast(
        spread > IRREGULAR_RATIO ? Basis.IRREGULAR : Basis.MEASURED,
        remaining, last.getOrderedOn().plusDays(remaining), gaps, usage,
        last.getOrderedOn(), last.getQuantity());
  }

  private static ConsumableForecast configured(
      Integer cycleDays, ConsumableOrder last, LocalDate today) {
    if (cycleDays == null || cycleDays <= 0) {
      return new ConsumableForecast(Basis.NONE, null, null, List.of(), null,
          last == null ? null : last.getOrderedOn(), last == null ? null : last.getQuantity());
    }
    LocalDate from = last == null ? today : last.getOrderedOn();
    return new ConsumableForecast(Basis.CONFIGURED, cycleDays, from.plusDays(cycleDays),
        List.of(), null, last == null ? null : last.getOrderedOn(),
        last == null ? null : last.getQuantity());
  }

  /**
   * 표본 선택: 기본은 최근 {@value #WINDOW_DAYS}일. 그 안에 간격이 2개 미만이면 최근
   * {@value #FALLBACK_ORDERS}건까지 넓힌다 — 분기마다 사는 품목도 예측이 나오게 하기 위해서다
   * ("3개월"을 기간으로만 읽으면 드물게 사는 품목은 영영 표본이 안 모인다).
   */
  private static List<ConsumableOrder> sample(List<ConsumableOrder> sorted, LocalDate today) {
    LocalDate from = today.minusDays(WINDOW_DAYS);
    List<ConsumableOrder> recent = sorted.stream()
        .filter(o -> !o.getOrderedOn().isBefore(from))
        .toList();
    if (recent.size() >= 3) {
      return recent;
    }
    int start = Math.max(0, sorted.size() - FALLBACK_ORDERS);
    return sorted.subList(start, sorted.size());
  }

  /** 같은 날 여러 번 주문한 것은 한 건으로 합친다 — 간격 0은 소비율을 무한대로 만든다. */
  private static List<ConsumableOrder> merged(List<ConsumableOrder> orders) {
    List<ConsumableOrder> sorted = new ArrayList<>(orders);
    sorted.sort(Comparator.comparing(ConsumableOrder::getOrderedOn));
    List<ConsumableOrder> out = new ArrayList<>();
    for (ConsumableOrder order : sorted) {
      if (!out.isEmpty() && out.get(out.size() - 1).getOrderedOn().equals(order.getOrderedOn())) {
        ConsumableOrder prev = out.remove(out.size() - 1);
        out.add(new ConsumableOrder(prev.getId(), prev.getTenantId(), prev.getItemId(),
            prev.getOrderedOn(), prev.getQuantity() + order.getQuantity(), prev.getNote(),
            prev.getCreatedBy(), prev.getCreatedAt()));
      } else {
        out.add(order);
      }
    }
    return out;
  }

  private static double median(List<Double> values) {
    List<Double> s = new ArrayList<>(values);
    s.sort(Comparator.naturalOrder());
    int n = s.size();
    return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
  }

  private static double max(List<Double> v) {
    return v.stream().mapToDouble(Double::doubleValue).max().orElse(0);
  }

  private static double min(List<Double> v) {
    return v.stream().mapToDouble(Double::doubleValue).min().orElse(0);
  }

  /** 오늘이 예상 시점을 지났는지. 근거가 없으면 절대 참이 아니다. */
  public boolean isDue(LocalDate today) {
    return dueOn != null && !today.isBefore(dueOn);
  }

  /** 실측 주기가 설정값과 크게 어긋나는지(보정 제안용). 30% 이상 차이면 알려 준다. */
  public boolean suggestsCycleChange(Integer configured) {
    if (basis != Basis.MEASURED || cycleDays == null || configured == null || configured <= 0) {
      return false;
    }
    return Math.abs(cycleDays - configured) / (double) configured >= 0.3;
  }

  public Basis basis() { return basis; }

  public Integer cycleDays() { return cycleDays; }

  public LocalDate dueOn() { return dueOn; }

  public List<Long> intervals() { return intervals; }

  public Double dailyUsage() { return dailyUsage; }

  public LocalDate lastOrderedOn() { return lastOrderedOn; }

  public Integer lastQuantity() { return lastQuantity; }
}
