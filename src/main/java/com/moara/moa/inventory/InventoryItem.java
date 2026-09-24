package com.moara.moa.inventory;

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
 * 실물·SW 인벤토리 항목. 자산 관리자가 등록하고 사용자에게 배정·회수하며 만료(라이선스)를 추적한다.
 * 배정/회수/폐기는 상태 전이 메서드로만 바뀐다(외부에서 setter 없음).
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  private String name;

  @Column(name = "item_type")
  @Enumerated(EnumType.STRING)
  private InventoryItemType type;

  private String category;

  @Column(name = "serial_no")
  private String serialNo;

  @Enumerated(EnumType.STRING)
  private InventoryItemStatus status;

  @Column(name = "assigned_user_id")
  private UUID assignedUserId;

  @Column(name = "expires_at")
  private LocalDate expiresAt;

  // 라이프사이클: 구매일 / 보증 만기 / 리스·계약 만기. 보증·리스 만기는 만료 통합 대시보드에 흐른다.
  @Column(name = "purchase_date")
  private LocalDate purchaseDate;

  @Column(name = "warranty_ends")
  private LocalDate warrantyEnds;

  @Column(name = "lease_ends")
  private LocalDate leaseEnds;

  private String note;

  // 소유팀(그룹). 지정 시 그 팀원이 자기 팀 소유 자산을 조회. NULL=자산관리자 전용(기존 동작).
  @Column(name = "owner_group_id")
  private UUID ownerGroupId;

  /**
   * 장착된 상위 장비. 활성 PARENT_ITEM 보관 구간의 파생 캐시다 — 이력은 장부가, 현재 상태는
   * 이 컬럼이 담당한다. null이면 그 자체로 하나의 자산이다.
   */
  @Column(name = "parent_item_id")
  private UUID parentItemId;

  /** 같은 부품 여러 개를 한 행으로(RAM 32GB 2개). 시리얼이 있으면 쪼갤 수 없어 1로 고정된다. */
  @Column(nullable = false)
  private int quantity = 1;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected InventoryItem() {}

  public InventoryItem(UUID id, UUID tenantId, InventoryItemForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.status = InventoryItemStatus.AVAILABLE;
    this.createdAt = now;
    applyMeta(form, now);
  }

  /** 메타(이름·유형·카테고리·시리얼·만료·비고) 갱신. 상태·배정은 건드리지 않는다. */
  public void applyMeta(InventoryItemForm form, OffsetDateTime now) {
    this.name = form.name();
    this.type = form.type();
    this.category = blankToNull(form.category());
    this.serialNo = blankToNull(form.serialNo());
    this.expiresAt = form.expiresAt();
    this.purchaseDate = form.purchaseDate();
    this.warrantyEnds = form.warrantyEnds();
    this.leaseEnds = form.leaseEnds();
    this.note = blankToNull(form.note());
    // 시리얼이 있으면 개체 하나를 가리키므로 수량은 항상 1이다.
    this.quantity = blankToNull(form.serialNo()) != null ? 1 : form.quantityOrOne();
    this.updatedAt = now;
  }

  /** 사용자에게 배정 → 상태 ASSIGNED. */
  public void assignTo(UUID userId, OffsetDateTime now) {
    this.assignedUserId = userId;
    this.status = InventoryItemStatus.ASSIGNED;
    this.updatedAt = now;
  }

  /** 회수 → 배정 해제 + 상태 AVAILABLE. */
  public void reclaim(OffsetDateTime now) {
    this.assignedUserId = null;
    this.status = InventoryItemStatus.AVAILABLE;
    this.updatedAt = now;
  }

  /** 폐기(불용) → 배정 해제 + 상태 RETIRED. 이력을 위해 레코드는 남긴다. */
  public void retire(OffsetDateTime now) {
    this.assignedUserId = null;
    this.status = InventoryItemStatus.RETIRED;
    this.updatedAt = now;
  }

  /** 소유팀 배정/해제(자산 관리자). null=해제(자산관리자 전용으로). */
  /**
   * 상위 장비에 장착하거나({@code parentItemId != null}) 떼어낸다. 부품으로 들어가면 개별
   * 배정을 쓰지 않는다 — 부모가 누구에게 갔는지가 곧 부품의 위치이고, 따로 관리하면
   * "노트북은 김개발에게, 그 안의 RAM은 창고에" 같은 모순이 장부에 남는다.
   */
  public void attachTo(UUID parentItemId, OffsetDateTime now) {
    this.parentItemId = parentItemId;
    if (parentItemId != null) {
      this.assignedUserId = null;
      this.status = InventoryItemStatus.ASSIGNED;
    } else if (this.status == InventoryItemStatus.ASSIGNED && this.assignedUserId == null) {
      this.status = InventoryItemStatus.AVAILABLE;
    }
    this.updatedAt = now;
  }

  /** 수량 변경(부분 이동으로 갈라질 때). 0 이하로는 내려가지 않는다. */
  public void changeQuantity(int quantity, OffsetDateTime now) {
    this.quantity = Math.max(1, quantity);
    this.updatedAt = now;
  }

  public void assignOwnerGroup(UUID ownerGroupId, OffsetDateTime now) {
    this.ownerGroupId = ownerGroupId;
    this.updatedAt = now;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getName() { return name; }
  public InventoryItemType getType() { return type; }
  public String getCategory() { return category; }
  public String getSerialNo() { return serialNo; }
  public InventoryItemStatus getStatus() { return status; }
  public UUID getAssignedUserId() { return assignedUserId; }
  public LocalDate getExpiresAt() { return expiresAt; }
  public LocalDate getPurchaseDate() { return purchaseDate; }
  public LocalDate getWarrantyEnds() { return warrantyEnds; }
  public LocalDate getLeaseEnds() { return leaseEnds; }
  public String getNote() { return note; }
  public UUID getOwnerGroupId() { return ownerGroupId; }
  public UUID getParentItemId() { return parentItemId; }
  public int getQuantity() { return quantity; }
  public boolean isPart() { return parentItemId != null; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
