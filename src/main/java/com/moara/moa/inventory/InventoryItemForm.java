package com.moara.moa.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 인벤토리 등록·수정 폼. 배정·상태는 별도 액션으로 관리한다. 유형(실물/SW)은 웹 등록 시 카테고리 경로의
 * 최상위에서 파생되므로 nullable(=CSV 등 다른 경로는 직접 지정). category는 웹 폼에서 필수(경로).
 */
public record InventoryItemForm(
    @NotBlank @Size(max = 100) String name,
    InventoryItemType type,
    @Size(max = 100) String category,
    @Size(max = 100) String serialNo,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate warrantyEnds,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate leaseEnds,
    @Size(max = 500) String note) {

  /** 라이프사이클 필드 도입 이전 호출부(테스트/기본 폼) 호환용. 구매·보증·리스는 미지정(null). */
  public InventoryItemForm(
      String name, InventoryItemType type, String category, String serialNo,
      LocalDate expiresAt, String note) {
    this(name, type, category, serialNo, expiresAt, null, null, null, note);
  }
}
