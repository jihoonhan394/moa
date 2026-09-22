package com.moara.moa.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "assets")
public class Asset {
  @Id private UUID id;

  // 생성 시 확정되며 이후 변경하지 않는다(자산의 테넌트 이동은 1차 범위 밖).
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "asset_type", nullable = false, length = 20)
  private AssetType assetType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AssetProtocol protocol;

  @Column(length = 255)
  private String host;

  private Integer port;

  @Column(length = 2048)
  private String url;

  @Column(name = "os_type", length = 20)
  private String osType;

  /** 서버 카테고리 경로(예: 내부망 서버 / 웹서버). 자유텍스트 저장(관리형 트리에서 선택). */
  @Column(length = 100)
  private String category;

  /** OS 계열(필터·통계). 카테고리와 별개 축. */
  @Enumerated(EnumType.STRING)
  @Column(name = "os_family", length = 20)
  private OsFamily osFamily;

  // 하드웨어 사양(수동 입력, 자유 텍스트). 서버 상세·접속 화면에 표시.
  @Column(length = 100)
  private String cpu;

  @Column(length = 50)
  private String ram;

  @Column(length = 100)
  private String disk;

  @Column(name = "hw_model", length = 100)
  private String hwModel;

  @Column(length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AssetStatus status;

  // 소유팀(그룹). 지정 시 그 팀원이 이 서버에 접근(연결) 가능. NULL=기존 권한 모델만(기존 동작).
  @Column(name = "owner_group_id")
  private UUID ownerGroupId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Asset() {}

  public Asset(UUID id, UUID tenantId, AssetForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    update(form, now);
    this.createdAt = now;
  }

  public void update(AssetForm form, OffsetDateTime now) {
    this.name = form.name().trim();
    this.assetType = form.assetType();
    this.protocol = form.protocol();
    this.host = trimToNull(form.host());
    this.port = form.port();
    this.url = trimToNull(form.url());
    this.osType = trimToNull(form.osType());
    this.category = trimToNull(form.category());
    this.osFamily = form.osFamily();
    this.cpu = trimToNull(form.cpu());
    this.ram = trimToNull(form.ram());
    this.disk = trimToNull(form.disk());
    this.hwModel = trimToNull(form.hwModel());
    this.description = trimToNull(form.description());
    this.status = form.status();
    this.updatedAt = now;
  }

  /** 소유팀 배정/해제(인프라 관리자). null=해제. */
  public void assignOwnerGroup(UUID ownerGroupId, OffsetDateTime now) {
    this.ownerGroupId = ownerGroupId;
    this.updatedAt = now;
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getOwnerGroupId() { return ownerGroupId; }
  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public String getName() { return name; }
  public AssetType getAssetType() { return assetType; }
  public AssetProtocol getProtocol() { return protocol; }
  public String getHost() { return host; }
  public Integer getPort() { return port; }
  public String getUrl() { return url; }
  public String getOsType() { return osType; }
  public String getCategory() { return category; }
  public OsFamily getOsFamily() { return osFamily; }
  public String getCpu() { return cpu; }
  public String getRam() { return ram; }
  public String getDisk() { return disk; }
  public String getHwModel() { return hwModel; }
  public boolean hasSpecs() {
    return cpu != null || ram != null || disk != null || hwModel != null;
  }
  public String getDescription() { return description; }
  public AssetStatus getStatus() { return status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
