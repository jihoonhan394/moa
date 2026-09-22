package com.moara.moa.inventory;

import java.util.List;

/**
 * CSV 대량 등록 결과. 행 단위로 독립 처리하며(한 행 실패가 나머지를 막지 않음), 실패 행은 사유와 함께 돌려준다.
 */
public record InventoryImportResult(int created, List<RowError> errors) {
  public record RowError(int line, String message, String raw) {}

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public int total() {
    return created + errors.size();
  }
}
