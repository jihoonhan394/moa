package com.moara.moa.inventory;

/** 지금 상태에서 할 수 없는 자산 상태 전이. 메시지는 화면에 그대로 보여 줄 수 있는 문장이다. */
public class InventoryStateException extends RuntimeException {
  public InventoryStateException(String message) {
    super(message);
  }
}
