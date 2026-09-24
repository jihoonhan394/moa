package com.moara.moa.consumable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 주문 기록 폼. 입력이 적을수록 실제로 쓴다 — <b>주문 등록이 귀찮으면 이 기능 전체가
 * 무의미해진다</b>(계산식보다 중요한 설계 조건이다). 날짜는 오늘, 수량은 직전 주문량을
 * 화면이 기본값으로 채운다.
 */
public record ConsumableOrderForm(
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderedOn,
    @Min(1) Integer quantity,
    @Size(max = 500) String note) {

  public int quantityOrOne() {
    return quantity == null || quantity < 1 ? 1 : quantity;
  }
}
