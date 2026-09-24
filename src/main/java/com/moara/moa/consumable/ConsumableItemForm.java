package com.moara.moa.consumable;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소모품 품목 등록·수정 폼.
 *
 * <p>{@code cycleDays}(예상 주기)는 선택이지만 <b>넣어 두면 첫날부터 알림이 돈다</b> —
 * 비우면 주문이 3회 쌓일 때까지 기다려야 한다. 실측이 모이면 보정을 제안한다.
 */
public record ConsumableItemForm(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 100) String category,
    @Size(max = 20) String unit,
    @Min(1) Integer cycleDays,
    @Size(max = 500) String note) {
}
