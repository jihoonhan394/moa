package com.moara.moa.solution;

import com.moara.moa.remote.RemoteProtocol;
import com.moara.moa.support.Values;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 제어 대상 솔루션(서비스/exe/데몬). 서버 자산에 속하며 제어 자격증명을 참조한다.
 * 단일 서버면 이대로 start/stop/status 대상이 되고, 이중화면 그룹으로 묶인다(Phase 2).
 */
@Entity
@Table(name = "managed_solutions")
public class ManagedSolution {
  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SolutionType type;

  @Column(nullable = false, length = 500)
  private String identifier;

  /** 관리형 카테고리 경로(예: 그룹웨어 / WAS). SolutionType(기술 유형)과 별개 그룹핑. */
  @Column(length = 100)
  private String category;

  @Column(name = "credential_id")
  private UUID credentialId;

  @Enumerated(EnumType.STRING)
  @Column(name = "health_check_type", nullable = false, length = 20)
  private HealthCheckType healthCheckType;

  @Column(name = "health_check_target", length = 500)
  private String healthCheckTarget;

  @Column(name = "start_command", length = 1000)
  private String startCommand;

  @Column(name = "stop_command", length = 1000)
  private String stopCommand;

  @Column(name = "status_command", length = 1000)
  private String statusCommand;

  @Enumerated(EnumType.STRING)
  @Column(name = "control_protocol", nullable = false, length = 20)
  private RemoteProtocol controlProtocol;

  @Column(name = "control_port")
  private Integer controlPort;

  // 소유팀(그룹). 지정 시 그 팀원은 개인 배정 없이도 운영(제어) 가능. NULL=인프라 전용(기존 동작).
  @Column(name = "owner_group_id")
  private UUID ownerGroupId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SolutionStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  // 운영 콘솔: 위키 매뉴얼 연결 + 유지보수 업체 연락처(우리 솔루션이 아닐 수 있음).
  @Column(name = "wiki_space_id")
  private UUID wikiSpaceId;

  @Column(name = "vendor_name")
  private String vendorName;

  @Column(name = "vendor_contact")
  private String vendorContact;

  @Column(name = "vendor_note")
  private String vendorNote;

  // 로그 수집 명령(온디맨드 분석). 비면 로그 분석 비활성.
  @Column(name = "log_command")
  private String logCommand;

  protected ManagedSolution() {}

  public ManagedSolution(UUID id, UUID tenantId, SolutionForm form, OffsetDateTime now) {
    this.id = id;
    this.tenantId = tenantId;
    this.assetId = form.assetId();
    this.createdAt = now;
    apply(form, now);
  }

  public void apply(SolutionForm form, OffsetDateTime now) {
    this.name = form.name().trim();
    this.type = form.type();
    this.identifier = form.identifier().trim();
    this.category = blankToNull(form.category());
    this.credentialId = form.credentialId();
    this.healthCheckType = form.healthCheckType();
    this.healthCheckTarget =
        form.healthCheckTarget() == null || form.healthCheckTarget().isBlank() ? null : form.healthCheckTarget().trim();
    this.startCommand = blankToNull(form.startCommand());
    this.stopCommand = blankToNull(form.stopCommand());
    this.statusCommand = blankToNull(form.statusCommand());
    this.controlProtocol = form.controlProtocol() == null ? RemoteProtocol.SSH : form.controlProtocol();
    this.controlPort = form.controlPort();
    this.ownerGroupId = form.ownerGroupId();
    this.status = form.status();
    this.updatedAt = now;
  }

  /** 소유팀 재배정(인프라 관리자). null=소유팀 해제(인프라 전용으로). */
  public void assignOwnerGroup(UUID ownerGroupId, OffsetDateTime now) {
    this.ownerGroupId = ownerGroupId;
    this.updatedAt = now;
  }

  /** 운영 정보(위키 매뉴얼·유지보수 업체·로그 명령) 갱신. 제어 설정과 분리된 별도 폼. */
  public void updateOps(UUID wikiSpaceId, String vendorName, String vendorContact, String vendorNote,
      String logCommand, OffsetDateTime now) {
    this.wikiSpaceId = wikiSpaceId;
    this.vendorName = blankToNull(vendorName);
    this.vendorContact = blankToNull(vendorContact);
    this.vendorNote = blankToNull(vendorNote);
    this.logCommand = blankToNull(logCommand);
    this.updatedAt = now;
  }

  private static String blankToNull(String value) {
    return Values.blankToNull(value);
  }

  public UUID getWikiSpaceId() { return wikiSpaceId; }
  public String getVendorName() { return vendorName; }
  public String getVendorContact() { return vendorContact; }
  public String getVendorNote() { return vendorNote; }
  public String getLogCommand() { return logCommand; }

  public UUID getId() { return id; }
  public UUID getTenantId() { return tenantId; }
  public UUID getAssetId() { return assetId; }
  public String getName() { return name; }
  public SolutionType getType() { return type; }
  public String getIdentifier() { return identifier; }
  public String getCategory() { return category; }
  public UUID getCredentialId() { return credentialId; }
  public HealthCheckType getHealthCheckType() { return healthCheckType; }
  public String getHealthCheckTarget() { return healthCheckTarget; }
  public String getStartCommand() { return startCommand; }
  public String getStopCommand() { return stopCommand; }
  public String getStatusCommand() { return statusCommand; }
  public RemoteProtocol getControlProtocol() { return controlProtocol; }
  public Integer getControlPort() { return controlPort; }
  public UUID getOwnerGroupId() { return ownerGroupId; }
  public SolutionStatus getStatus() { return status; }
  public OffsetDateTime getCreatedAt() { return createdAt; }
  public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
