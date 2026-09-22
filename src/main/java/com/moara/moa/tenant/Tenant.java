package com.moara.moa.tenant;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {
  /** V3 migration이 시드하는 기본 테넌트의 고정 PK. backfill/코드가 결정적으로 참조한다. */
  public static final UUID DEFAULT_TENANT_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** 기본 테넌트 식별 코드. V17에서 MOA→MTCM으로 변경(데모 기관 코드). */
  public static final String DEFAULT_TENANT_CODE = "MTCM";

  @Id private UUID id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TenantStatus status;

  // 구독(사용기간). 널=무제한.
  @Column(name = "subscription_start")
  private LocalDate subscriptionStart;

  @Column(name = "subscription_end")
  private LocalDate subscriptionEnd;

  // 기관이 켠 기능 모듈 집합(tenant_features). 메뉴 노출/라우트 접근을 강제한다.
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "tenant_features", joinColumns = @JoinColumn(name = "tenant_id"))
  @Column(name = "feature", length = 30)
  @Enumerated(EnumType.STRING)
  private Set<FeatureModule> features = new HashSet<>();

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Tenant() {}

  public Tenant(UUID id, CreateTenantCommand command, OffsetDateTime now) {
    this.id = id;
    this.name = command.name().trim();
    this.code = command.code().trim();
    this.status = TenantStatus.ACTIVE;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void disable(OffsetDateTime now) {
    this.status = TenantStatus.DISABLED;
    this.updatedAt = now;
  }

  public void enable(OffsetDateTime now) {
    this.status = TenantStatus.ACTIVE;
    this.updatedAt = now;
  }

  public boolean isActive() {
    return status == TenantStatus.ACTIVE;
  }

  public void updateSubscription(LocalDate start, LocalDate end, OffsetDateTime now) {
    this.subscriptionStart = start;
    this.subscriptionEnd = end;
    this.updatedAt = now;
  }

  public void setFeatures(Set<FeatureModule> features, OffsetDateTime now) {
    this.features = new HashSet<>(features == null ? Set.of() : features);
    this.updatedAt = now;
  }

  public boolean hasFeature(FeatureModule feature) {
    return features.contains(feature);
  }

  /** 구독 만료 여부. 만기일 미설정이면 무제한(만료 아님). */
  public boolean isExpired(LocalDate today) {
    return subscriptionEnd != null && subscriptionEnd.isBefore(today);
  }

  /** 로그인/접근 가능 여부: 활성 상태 + 미만료. */
  public boolean isAccessible(LocalDate today) {
    return isActive() && !isExpired(today);
  }

  /** 만기 임박: 만기일이 있고 아직 만료 전이며 today 기준 7일 이내(무제한/이미 만료는 제외). */
  public boolean isExpiringSoon(LocalDate today) {
    return subscriptionEnd != null && !isExpired(today) && !subscriptionEnd.isAfter(today.plusDays(7));
  }

  public UUID getId() { return id; }
  public String getName() { return name; }
  public String getCode() { return code; }
  public TenantStatus getStatus() { return status; }
  public LocalDate getSubscriptionStart() { return subscriptionStart; }
  public LocalDate getSubscriptionEnd() { return subscriptionEnd; }
  public Set<FeatureModule> getFeatures() { return features; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
