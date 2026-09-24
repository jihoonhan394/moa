package com.moara.moa.inventory;

import jakarta.validation.constraints.Min;
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
    @Size(max = 500) String note,
    /**
     * 같은 물건 여러 개를 한 건으로 등록할 때의 개수(예: RAM 32GB 2개). 비우면 1이다.
     * 부품을 다른 장비로 <b>일부만</b> 옮기려면 이 값이 있어야 한다.
     * 시리얼이 있는 물건은 개체 하나를 가리키므로 쪼갤 수 없다.
     */
    @Min(1) Integer quantity) {

  /** 라이프사이클 필드 도입 이전 호출부(테스트/기본 폼) 호환용. 구매·보증·리스는 미지정(null). */
  public InventoryItemForm(
      String name, InventoryItemType type, String category, String serialNo,
      LocalDate expiresAt, String note) {
    this(name, type, category, serialNo, expiresAt, null, null, null, note, null);
  }

  /** 수량 도입 이전 호출부 호환용(수량 1). */
  public InventoryItemForm(
      String name, InventoryItemType type, String category, String serialNo,
      LocalDate expiresAt, LocalDate purchaseDate, LocalDate warrantyEnds, LocalDate leaseEnds,
      String note) {
    this(name, type, category, serialNo, expiresAt, purchaseDate, warrantyEnds, leaseEnds, note, null);
  }

  /** 비었으면 1. 화면에서 안 채워도 기존 동작(1개)이 그대로 유지된다. */
  public int quantityOrOne() {
    return quantity == null || quantity < 1 ? 1 : quantity;
  }
}
