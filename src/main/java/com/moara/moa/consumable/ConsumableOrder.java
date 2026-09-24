package com.moara.moa.consumable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 소모품 주문 한 건. <b>이 날짜들의 간격과 수량이 예측의 전부</b>다.
 *
 * <p>append-only로 다룬다 — 주문은 실제로 돈이 나간 사실이고, 지우면 과거 소비율이 통째로
 * 바뀐다. 잘못 넣었으면 고치되(같은 행 수정) 없던 일로 만들지 않는다.
 */
@Entity
@Table(name = "consumable_orders")
public class ConsumableOrder {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  /** 주문(또는 입고)일. 발주일·입고일 중 무엇이든 일관되기만 하면 간격은 같다. */
  @Column(name = "ordered_on", nullable = false)
  private LocalDate orderedOn;

  /** 이번에 산 양. 다음 주문까지 소비된 양으로 본다. */
  @Column(nullable = false)
  private int quantity = 1;

  @Column(length = 500)
  private String note;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ConsumableOrder() {
  }

  public ConsumableOrder(
      UUID id, UUID tenantId, UUID itemId, LocalDate orderedOn, int quantity, String note,
      UUID createdBy, OffsetDateTime createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.itemId = itemId;
    this.orderedOn = orderedOn;
    this.quantity = Math.max(1, quantity);
    this.note = note == null || note.isBlank() ? null : note.trim();
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID getId() { return id; }

  public UUID getTenantId() { return tenantId; }

  public UUID getItemId() { return itemId; }

  public LocalDate getOrderedOn() { return orderedOn; }

  public int getQuantity() { return quantity; }

  public String getNote() { return note; }

  public UUID getCreatedBy() { return createdBy; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
}
