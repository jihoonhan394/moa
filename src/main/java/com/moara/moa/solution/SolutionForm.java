package com.moara.moa.solution;

import com.moara.moa.remote.RemoteProtocol;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 제어 대상 솔루션 등록/수정 폼. */
public record SolutionForm(
    @NotNull UUID assetId,
    @NotBlank @Size(max = 100) String name,
    @NotNull SolutionType type,
    @NotBlank @Size(max = 500) String identifier,
    UUID credentialId,
    @NotNull HealthCheckType healthCheckType,
    @Size(max = 500) String healthCheckTarget,
    @NotNull SolutionStatus status,
    // CUSTOM_COMMAND 유형에서 사용(그 외 유형은 무시).
    @Size(max = 1000) String startCommand,
    @Size(max = 1000) String stopCommand,
    @Size(max = 1000) String statusCommand,
    // 제어 채널. 미지정 시 SSH. 포트 미지정 시 프로토콜별 기본(SSH=자산 포트, WinRM=5985)을 사용.
    @NotNull RemoteProtocol controlProtocol,
    @Min(1) @Max(65535) Integer controlPort,
    // 소유팀(그룹). 지정 시 그 팀원이 개인 배정 없이 운영 가능. null=인프라 전용.
    UUID ownerGroupId,
    // 관리형 카테고리 경로(그룹핑). SolutionType(기술 유형)과 별개. null=미분류.
    @Size(max = 100) String category) {

  /** 카테고리 도입 이전 호출부 호환(소유팀은 지정, 카테고리 미지정). */
  public SolutionForm(
      UUID assetId, String name, SolutionType type, String identifier, UUID credentialId,
      HealthCheckType healthCheckType, String healthCheckTarget, SolutionStatus status,
      String startCommand, String stopCommand, String statusCommand,
      RemoteProtocol controlProtocol, Integer controlPort, UUID ownerGroupId) {
    this(assetId, name, type, identifier, credentialId, healthCheckType, healthCheckTarget, status,
        startCommand, stopCommand, statusCommand, controlProtocol, controlPort, ownerGroupId, null);
  }

  /** 소유팀·카테고리 도입 이전 호출부 호환(둘 다 null). */
  public SolutionForm(
      UUID assetId, String name, SolutionType type, String identifier, UUID credentialId,
      HealthCheckType healthCheckType, String healthCheckTarget, SolutionStatus status,
      String startCommand, String stopCommand, String statusCommand,
      RemoteProtocol controlProtocol, Integer controlPort) {
    this(assetId, name, type, identifier, credentialId, healthCheckType, healthCheckTarget, status,
        startCommand, stopCommand, statusCommand, controlProtocol, controlPort, null, null);
  }
}
