package com.moara.moa.reservation;

import com.moara.moa.support.Values;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
// 유형(type)은 고정 enum이었으나 자산 관리자가 CRUD 하는 관리형 카테고리(String)로 대체됨.
import java.time.OffsetDateTime;
import java.util.UUID;

/** 공유자산(차량·회의실·좌석 등). 자산 관리자가 등록하고 일반 사용자가 예약한다. */
@Entity
@Table(name = "shared_resources")
public class SharedResource {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  private String name;

  private String category;

  private String location;

  private Integer capacity;

  @Enumerated(EnumType.STRING)
  private SharedResourceStatus status;

  private String description;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected SharedResource() {}

  public SharedResource(UUID id, UUID tenantId, SharedResourceForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.createdAt = now;
    apply(form, now);
  }

  public void apply(SharedResourceForm form, OffsetDateTime now) {
    this.name = form.name();
    this.category = blankToNull(form.category());
    this.location = blankToNull(form.location());
    this.capacity = form.capacity();
    this.status = form.status() == null ? SharedResourceStatus.ACTIVE : form.status();
    this.description = blankToNull(form.description());
    this.updatedAt = now;
  }

  public boolean isBookable() {
    return status == SharedResourceStatus.ACTIVE;
  }

  private static String blankToNull(String value) {
    return Values.blankToNull(value);
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getName() { return name; }
  public String getCategory() { return category; }
  public String getLocation() { return location; }
  public Integer getCapacity() { return capacity; }
  public SharedResourceStatus getStatus() { return status; }
  public String getDescription() { return description; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
