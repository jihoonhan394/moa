package com.moara.moa.solution;

import com.moara.moa.asset.AssetService;
import com.moara.moa.credential.CredentialService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제어 대상 솔루션 등록/관리. 자산·자격증명이 같은 테넌트에 속하는지 각 도메인 서비스로 검증한다
 * (타 테넌트는 NotFound = 격리). 실제 제어(start/stop/status)는 이후 실행 어댑터(1c/1d)에서 이 정의를 사용한다.
 */
@Service
@Transactional(readOnly = true)
public class ManagedSolutionService {
  private final ManagedSolutionRepository solutionRepository;
  private final AssetService assetService;
  private final CredentialService credentialService;
  private final Clock clock;

  @Autowired
  public ManagedSolutionService(
      ManagedSolutionRepository solutionRepository, AssetService assetService, CredentialService credentialService) {
    this(solutionRepository, assetService, credentialService, Clock.systemUTC());
  }

  ManagedSolutionService(
      ManagedSolutionRepository solutionRepository, AssetService assetService,
      CredentialService credentialService, Clock clock) {
    this.solutionRepository = solutionRepository;
    this.assetService = assetService;
    this.credentialService = credentialService;
    this.clock = clock;
  }

  @Transactional
  public ManagedSolution create(UUID tenantId, SolutionForm form) {
    validateReferences(tenantId, form);
    solutionRepository.findByTenantIdAndAssetIdAndName(tenantId, form.assetId(), form.name().trim())
        .ifPresent(existing -> {
          throw new DuplicateSolutionException(form.name());
        });
    return solutionRepository.save(new ManagedSolution(UUID.randomUUID(), tenantId, form, OffsetDateTime.now(clock)));
  }

  public ManagedSolution findById(UUID tenantId, UUID id) {
    return solutionRepository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new SolutionNotFoundException(id));
  }

  public List<ManagedSolution> findAll(UUID tenantId) {
    return solutionRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  public List<ManagedSolution> findByAsset(UUID tenantId, UUID assetId) {
    return solutionRepository.findAllByTenantIdAndAssetIdOrderByNameAsc(tenantId, assetId);
  }

  @Transactional
  public ManagedSolution update(UUID tenantId, UUID id, SolutionForm form) {
    ManagedSolution solution = findById(tenantId, id);
    validateReferences(tenantId, form);
    solutionRepository.findByTenantIdAndAssetIdAndName(tenantId, form.assetId(), form.name().trim())
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new DuplicateSolutionException(form.name());
        });
    solution.apply(form, OffsetDateTime.now(clock));
    return solutionRepository.save(solution);
  }

  /** 운영 정보(위키 매뉴얼·유지보수 업체) 갱신. 지정한 위키 공간은 같은 기관 소유여야 한다(검증은 컨트롤러). */
  @Transactional
  public ManagedSolution updateOps(UUID tenantId, UUID id, SolutionOpsForm form) {
    ManagedSolution solution = findById(tenantId, id);
    solution.updateOps(form.wikiSpaceId(), form.vendorName(), form.vendorContact(), form.vendorNote(),
        form.logCommand(), OffsetDateTime.now(clock));
    return solutionRepository.save(solution);
  }

  /** 소유팀(그룹) 배정/해제. groupId=null이면 인프라 전용으로 되돌린다. */
  @Transactional
  public ManagedSolution assignOwnerGroup(UUID tenantId, UUID id, UUID groupId) {
    ManagedSolution solution = findById(tenantId, id);
    solution.assignOwnerGroup(groupId, OffsetDateTime.now(clock));
    return solutionRepository.save(solution);
  }

  @Transactional
  public void delete(UUID tenantId, UUID id) {
    solutionRepository.delete(findById(tenantId, id));
  }

  private void validateReferences(UUID tenantId, SolutionForm form) {
    assetService.findById(tenantId, form.assetId()); // 자산 소유권(타 테넌트 NotFound)
    if (form.credentialId() != null) {
      credentialService.findById(tenantId, form.credentialId()); // 자격증명 소유권
    }
  }
}
