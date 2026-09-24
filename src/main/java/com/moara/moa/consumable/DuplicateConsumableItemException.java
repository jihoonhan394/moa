package com.moara.moa.consumable;

/** 같은 기관에 같은 이름의 소모품이 이미 있을 때. */
public class DuplicateConsumableItemException extends RuntimeException {
  public DuplicateConsumableItemException(String name) {
    super("Consumable item already exists: " + name);
  }
}
