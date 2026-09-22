package com.moara.moa.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 공유자산 예약(자산×사용자×시간대). 시간은 벽시계(로컬) 기준. */
@Entity
@Table(name = "reservations")
public class Reservation {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "starts_at")
  private LocalDateTime startsAt;

  @Column(name = "ends_at")
  private LocalDateTime endsAt;

  private String purpose;

  @Enumerated(EnumType.STRING)
  private ReservationStatus status;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected Reservation() {}

  public Reservation(
      UUID id, UUID tenantId, UUID resourceId, UUID userId,
      LocalDateTime startsAt, LocalDateTime endsAt, String purpose, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.resourceId = resourceId;
    this.userId = userId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.purpose = purpose == null || purpose.isBlank() ? null : purpose.trim();
    this.status = ReservationStatus.BOOKED;
    this.createdAt = now;
  }

  public void cancel() {
    this.status = ReservationStatus.CANCELLED;
  }

  public boolean isBooked() {
    return status == ReservationStatus.BOOKED;
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getResourceId() { return resourceId; }
  public UUID getUserId() { return userId; }
  public LocalDateTime getStartsAt() { return startsAt; }
  public LocalDateTime getEndsAt() { return endsAt; }
  public String getPurpose() { return purpose; }
  public ReservationStatus getStatus() { return status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
