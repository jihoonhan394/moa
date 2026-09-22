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
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
