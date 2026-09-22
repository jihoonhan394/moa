package com.moara.moa.tracker;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 자원별(인벤토리/자산) 만기·점검 항목. 라벨+예정일, 선택적 반복주기. source로 수동/자동(SSL 프로브)을 구분한다.
 * 만료 통합 대시보드/알림에 흐른다. 금액·계약은 담지 않는다(운영 만기까지, 재무는 ERP).
 */
@Entity
@Table(name = "resource_trackers")
public class ResourceTracker {
  @Id
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "target_type")
  @Enumerated(EnumType.STRING)
  private TrackerTargetType targetType;

  @Column(name = "target_id")
  private UUID targetId;

  private String label;

  @Column(name = "due_on", nullable = false)
  private LocalDate dueOn;

  @Column(name = "recur_every_days")
  private Integer recurEveryDays;

  @Column(name = "last_done_on")
  private LocalDate lastDoneOn;

  private String source; // MANUAL / SSL_PROBE

  // SSL 자동감지 항목의 재프로브 대상(수동 항목은 null). 일 배치가 이 값으로 다시 조회한다.
  @Column(name = "probe_host")
  private String probeHost;

  @Column(name = "probe_port")
  private Integer probePort;

  // 인증서 상세 요약(발급자·유효기간·SAN). 표시 전용, secret 아님.
  @Column(name = "detail")
  private String detail;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  protected ResourceTracker() {}

  public ResourceTracker(
      UUID id, UUID tenantId, TrackerTargetType targetType, UUID targetId, String label,
      LocalDate dueOn, Integer recurEveryDays, String source, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.targetType = targetType;
    this.targetId = targetId;
    this.label = label == null ? null : label.trim();
    this.dueOn = dueOn;
    this.recurEveryDays = recurEveryDays != null && recurEveryDays > 0 ? recurEveryDays : null;
    this.source = source == null ? "MANUAL" : source;
    this.createdAt = now;
  }

  public boolean isRecurring() {
    return recurEveryDays != null && recurEveryDays > 0;
  }

  /** 완료: 반복이면 다음 예정일로 롤(오늘+주기), 1회성이면 완료일만 기록. */
  public void markDone(LocalDate today) {
    this.lastDoneOn = today;
    if (isRecurring()) {
      this.dueOn = today.plusDays(recurEveryDays);
    }
  }

  /** 자동 프로브가 만료일을 갱신할 때. */
  public void updateDue(LocalDate dueOn) {
    this.dueOn = dueOn;
  }

  /** 자동 프로브가 만료일 + 재프로브 대상(host/port) + 상세를 갱신할 때. */
  public void updateProbe(LocalDate dueOn, String probeHost, Integer probePort, String detail) {
    this.dueOn = dueOn;
    this.probeHost = probeHost;
    this.probePort = probePort;
    this.detail = detail;
  }

  /** 오늘 기준 남은 일수(음수면 만료 지남). */
  public long daysRemaining(LocalDate today) {
    return ChronoUnit.DAYS.between(today, dueOn);
  }

  /** 만기 심각도: EXPIRED(지남) / CRITICAL(≤7) / WARNING(≤30) / OK. 통합 대시보드·화면 색상용. */
  public String severity(LocalDate today) {
    long d = daysRemaining(today);
    if (d < 0) {
      return "EXPIRED";
    }
    if (d <= 7) {
      return "CRITICAL";
    }
    if (d <= 30) {
      return "WARNING";
    }
    return "OK";
  }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public TrackerTargetType getTargetType() { return targetType; }
  public UUID getTargetId() { return targetId; }
  public String getLabel() { return label; }
  public LocalDate getDueOn() { return dueOn; }
  public Integer getRecurEveryDays() { return recurEveryDays; }
  public LocalDate getLastDoneOn() { return lastDoneOn; }
  public String getSource() { return source; }
  public String getProbeHost() { return probeHost; }
  public Integer getProbePort() { return probePort; }
  public String getDetail() { return detail; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
}
