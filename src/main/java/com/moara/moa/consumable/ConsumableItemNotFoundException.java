package com.moara.moa.consumable;

import java.util.UUID;

/** 소모품 품목을 찾을 수 없을 때. 교차 기관 접근도 이 예외로 404가 된다. */
public class ConsumableItemNotFoundException extends RuntimeException {
  public ConsumableItemNotFoundException(UUID id) {
    super("Consumable item was not found: " + id);
  }
}
