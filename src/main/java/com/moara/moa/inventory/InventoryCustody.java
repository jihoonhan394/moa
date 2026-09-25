package com.moara.moa.inventory;

import com.moara.moa.support.Values;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 자산 보관 구간 한 줄. 이 물건을 <b>언제부터 언제까지 누가 갖고 있었는지</b>를 나타낸다.
 *
 * <p>구간이 열려 있으면({@code endedOn == null}) 지금 보유 중이다. 한 품목의 이력은 이 구간들을
 * 시간순으로 나열한 것이고, {@code InventoryItem.assignedUserId}·{@code status}는 활성 구간에서
 * 파생된 캐시일 뿐이다.
 *
 * <p>구간은 <b>닫기만 하고 지우지 않는다</b>. 장부의 값어치는 지난 기록에 있고, 지운 기록은
 * "이 장비가 어디를 거쳐 왔나"라는 질문에 답할 수 없게 만든다.
 */
@Entity
@Table(name = "inventory_custodies")
public class InventoryCustody {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "item_id", nullable = false)
  private UUID itemId;

  /** 이 구간이 덮는 수량. 부분 이동(RAM 2개 중 1개)을 위해 두며 기본은 1이다. */
  @Column(nullable = false)
  private int quantity = 1;

  @Enumerated(EnumType.STRING)
  @Column(name = "holder_type", nullable = false, length = 20)
  private InventoryHolderType holderType;

  /** 내부 대상(USER/GROUP/PARENT_ITEM)의 ID. 외부 거래처는 null이고 이름만 남는다. */
  @Column(name = "holder_id")
  private UUID holderId;

  /** 외부 거래처·업체 이름. 내부 대상은 null이며 이름은 조회 시점에 해석한다. */
  @Column(name = "holder_name", length = 200)
  private String holderName;

  @Column(name = "started_on", nullable = false)
  private LocalDate startedOn;

  /** null이면 현재 보유 중. */
  @Column(name = "ended_on")
  private LocalDate endedOn;

  /** 납품·대여·수리의 반납 예정일. 지났는데 구간이 열려 있으면 반납 초과다. */
  @Column(name = "expected_return_on")
  private LocalDate expectedReturnOn;

  @Column(length = 100)
  private String reason;

  @Column(length = 500)
  private String note;

  /** 배치(퇴사 회수 등)로 생긴 구간은 행위자를 알 수 없어 null이다. */
  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  /**
   * 받은 사람이 인수를 확인한 시각. 사용자 구간에만 의미가 있고, 창고·고객처 구간은 확인할
   * 사람이 없어 null로 남는다. 이 칸이 있어야 장부가 <b>양쪽의 기록</b>이 된다.
   */
  @Column(name = "confirmed_at")
  private OffsetDateTime confirmedAt;

  protected InventoryCustody() {
  }

  public InventoryCustody(
      UUID id, UUID tenantId, UUID itemId, int quantity, InventoryHolderType holderType,
      UUID holderId, String holderName, LocalDate startedOn, LocalDate expectedReturnOn,
      String reason, String note, UUID createdBy, OffsetDateTime createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.itemId = itemId;
    this.quantity = Math.max(1, quantity);
    this.holderType = holderType;
    this.holderId = holderId;
    this.holderName = blankToNull(holderName);
    this.startedOn = startedOn;
    this.expectedReturnOn = expectedReturnOn;
    this.reason = blankToNull(reason);
    this.note = blankToNull(note);
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  /** 보관 종료. 이미 닫힌 구간은 다시 닫지 않는다(먼저 기록된 날짜가 사실에 가깝다). */
  public void close(LocalDate on) {
    if (endedOn == null) {
      this.endedOn = on;
    }
  }

  public boolean isOpen() {
    return endedOn == null;
  }

  /**
   * 인수 확인. 이미 확인했으면 덮어쓰지 않는다 — 처음 확인한 시각이 사실이고, 나중 값으로
   * 밀리면 "언제 받았나"의 답이 바뀐다.
   */
  public void confirm(OffsetDateTime at) {
    if (confirmedAt == null) {
      this.confirmedAt = at;
    }
  }

  /** 확인이 필요한데 아직 안 한 구간인지. 사용자에게 간 열린 구간만 해당한다. */
  public boolean awaitsConfirmation() {
    return holderType == InventoryHolderType.USER && isOpen() && confirmedAt == null;
  }

  /** 반납 예정일이 지났는데 아직 돌아오지 않았는지. 예정일이 없으면 초과라는 개념도 없다. */
  public boolean isReturnOverdue(LocalDate today) {
    return isOpen() && expectedReturnOn != null && expectedReturnOn.isBefore(today);
  }

  private static String blankToNull(String value) {
    return Values.blankToNull(value);
  }

  public UUID getId() { return id; }

  public UUID getTenantId() { return tenantId; }

  public UUID getItemId() { return itemId; }

  public int getQuantity() { return quantity; }

  public InventoryHolderType getHolderType() { return holderType; }

  public UUID getHolderId() { return holderId; }

  public String getHolderName() { return holderName; }

  public LocalDate getStartedOn() { return startedOn; }

  public LocalDate getEndedOn() { return endedOn; }

  public LocalDate getExpectedReturnOn() { return expectedReturnOn; }

  public String getReason() { return reason; }

  public String getNote() { return note; }

  public UUID getCreatedBy() { return createdBy; }

  public OffsetDateTime getCreatedAt() { return createdAt; }

  public OffsetDateTime getConfirmedAt() { return confirmedAt; }
}
