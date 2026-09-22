package com.moara.moa.tracker;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자원 만기·점검 항목 관리(인벤토리/자산 공용). 1회성·반복을 한 그릇에 담아 만료 통합 대시보드/알림에 흘린다.
 * SSL 자동 프로브는 {@link #upsertProbed}로 자산의 만료일을 자동 채우고, {@link #refreshSslTrackers}가
 * 저장된 host/port로 주기적으로 재프로브해 만료일·상세를 갱신한다.
 */
@Service
@Transactional(readOnly = true)
public class ResourceTrackerService {
  private static final Logger log = LoggerFactory.getLogger(ResourceTrackerService.class);
  public static final String SOURCE_MANUAL = "MANUAL";
  public static final String SOURCE_SSL = "SSL_PROBE";

  private final ResourceTrackerRepository repository;
  private final SslProbeService sslProbeService;

  public ResourceTrackerService(ResourceTrackerRepository repository, SslProbeService sslProbeService) {
    this.repository = repository;
    this.sslProbeService = sslProbeService;
  }

  public List<ResourceTracker> forTarget(UUID tenantId, TrackerTargetType targetType, UUID targetId) {
    return repository.findAllByTenantIdAndTargetTypeAndTargetIdOrderByDueOnAsc(tenantId, targetType, targetId);
  }

  /** 만료 대시보드 집계용: 기관의 모든 항목. */
  public List<ResourceTracker> allForTenant(UUID tenantId) {
    return repository.findAllByTenantId(tenantId);
  }

  @Transactional
  public void add(
      UUID tenantId, TrackerTargetType targetType, UUID targetId, String label,
      LocalDate dueOn, Integer recurEveryDays) {
    if (label == null || label.isBlank() || dueOn == null) {
      return;
    }
    repository.save(new ResourceTracker(
        UUID.randomUUID(), tenantId, targetType, targetId, label, dueOn, recurEveryDays,
        SOURCE_MANUAL, OffsetDateTime.now()));
  }

  /** 완료: 반복이면 다음 예정일로 롤, 1회성이면 완료일 기록. */
  @Transactional
  public void markDone(UUID tenantId, UUID id) {
    repository.findByTenantIdAndId(tenantId, id).ifPresent(t -> t.markDone(LocalDate.now()));
  }

  @Transactional
  public void remove(UUID tenantId, UUID id) {
    repository.findByTenantIdAndId(tenantId, id).ifPresent(repository::delete);
  }

  /**
   * 자동 프로브(SSL 등) 결과로 자산의 만료일 항목을 갱신하거나 새로 만든다(대상+source로 단일 유지).
   * 재프로브 대상(host/port)·상세는 남기지 않는다(수동 호출·하위호환용).
   */
  @Transactional
  public void upsertProbed(
      UUID tenantId, TrackerTargetType targetType, UUID targetId, String label, LocalDate dueOn, String source) {
    upsertProbed(tenantId, targetType, targetId, label, dueOn, source, null, null, null);
  }

  /**
   * 자동 프로브 결과 upsert(재프로브용 host/port + 인증서 상세 포함). 일 배치가 host/port로 재조회한다.
   */
  @Transactional
  public void upsertProbed(
      UUID tenantId, TrackerTargetType targetType, UUID targetId, String label, LocalDate dueOn,
      String source, String probeHost, Integer probePort, String detail) {
    if (dueOn == null) {
      return;
    }
    repository.findFirstByTenantIdAndTargetTypeAndTargetIdAndSource(tenantId, targetType, targetId, source)
        .ifPresentOrElse(
            existing -> existing.updateProbe(dueOn, probeHost, probePort, detail),
            () -> {
              ResourceTracker tracker = new ResourceTracker(
                  UUID.randomUUID(), tenantId, targetType, targetId, label, dueOn, null, source,
                  OffsetDateTime.now());
              tracker.updateProbe(dueOn, probeHost, probePort, detail);
              repository.save(tracker);
            });
  }

  /**
   * SSL 자동감지 항목을 저장된 host/port로 재프로브해 만료일·상세를 갱신한다(스케줄러 전용).
   * host/port가 없는 과거 항목은 건너뛴다. 항목별 예외는 삼켜 전체 배치를 멈추지 않는다. 갱신 건수 반환.
   */
  @Transactional
  public int refreshSslTrackers() {
    int updated = 0;
    for (ResourceTracker tracker : repository.findAllBySource(SOURCE_SSL)) {
      if (tracker.getProbeHost() == null || tracker.getProbeHost().isBlank() || tracker.getProbePort() == null) {
        continue;
      }
      try {
        Optional<SslProbeService.ProbeResult> result =
            sslProbeService.probe(tracker.getProbeHost(), tracker.getProbePort());
        if (result.isEmpty()) {
          continue;
        }
        tracker.updateProbe(
            result.get().notAfter(), tracker.getProbeHost(), tracker.getProbePort(),
            SslProbeService.summarize(result.get()));
        updated++;
      } catch (RuntimeException exception) {
        log.info("[SSL] tracker refresh failed id={}: {}", tracker.getId(), exception.getMessage());
      }
    }
    return updated;
  }
}
