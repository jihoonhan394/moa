package com.moara.moa.deputy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 내 대직(임시 부재 권한) 관리·조회. 스코프(부재/대직 모두 지정자의 팀원인지)는 컨트롤러가 강제하고,
 * 여기서는 저장/조회와 <b>"지금 이 사용자가 대신하는 부재자들"</b> 계산을 담당한다(만료형 접근처럼 날짜로 활성 판정).
 */
@Service
@Transactional(readOnly = true)
public class DeputyService {
  private final DeputyDelegationRepository repository;

  public DeputyService(DeputyDelegationRepository repository) {
    this.repository = repository;
  }

  /** 오늘 기준, deputyUserId가 대직 중인 부재자 id 집합(접근 합집합의 대상). */
  public Set<UUID> coveredAbsentUsers(UUID tenantId, UUID deputyUserId) {
    LocalDate today = LocalDate.now();
    return repository.findAllByTenantIdAndDeputyUserId(tenantId, deputyUserId).stream()
        .filter(d -> d.isActiveOn(today))
        .map(DeputyDelegation::getAbsentUserId)
        .collect(Collectors.toSet());
  }

  public List<DeputyDelegation> findAll(UUID tenantId) {
    return repository.findAllByTenantIdOrderByStartsOnDesc(tenantId);
  }

  @Transactional
  public DeputyDelegation create(
      UUID tenantId, UUID absentUserId, UUID deputyUserId,
      LocalDate startsOn, LocalDate endsOn, UUID createdByUserId) {
    return repository.save(new DeputyDelegation(
        UUID.randomUUID(), tenantId, absentUserId, deputyUserId, startsOn, endsOn,
        createdByUserId, OffsetDateTime.now()));
  }

  @Transactional
  public void end(UUID tenantId, UUID id) {
    repository.findByTenantIdAndId(tenantId, id).ifPresent(repository::delete);
  }
}
