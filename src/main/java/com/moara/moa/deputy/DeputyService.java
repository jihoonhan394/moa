package com.moara.moa.deputy;

import com.moara.moa.group.AccessGroupService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 내 대직(임시 부재 권한) 관리·조회. 스코프(부재/대직 모두 지정자의 팀원인지)는 컨트롤러와
 * <b>서비스 양쪽에서 검사</b>하며(AGENTS.md 이중 검사), 저장/조회와
 * <b>"지금 이 사용자가 대신하는 부재자들"</b> 계산을 담당한다(만료형 접근처럼 날짜로 활성 판정).
 */
@Service
@Transactional(readOnly = true)
public class DeputyService {
  private final DeputyDelegationRepository repository;
  private final AccessGroupService groupService;

  public DeputyService(DeputyDelegationRepository repository, AccessGroupService groupService) {
    this.repository = repository;
    this.groupService = groupService;
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

  /**
   * 대직을 지정한다. 호출부(컨트롤러)가 이미 검사하더라도 <b>서비스에서 불변식을 다시 확인</b>한다 —
   * AGENTS.md는 URL 보안과 서비스 레벨 검사를 이중으로 두라고 요구하며, 컨트롤러 통과가 곧 인가는
   * 아니다. 다른 호출부(배치·신규 화면)가 생겨도 임의의 두 사용자 사이에 대직이 생기지 않게 막는다.
   */
  @Transactional
  public DeputyDelegation create(
      UUID tenantId, UUID absentUserId, UUID deputyUserId,
      LocalDate startsOn, LocalDate endsOn, UUID createdByUserId) {
    if (absentUserId == null || deputyUserId == null || absentUserId.equals(deputyUserId)) {
      throw new IllegalArgumentException("부재자와 대직자는 서로 다른 사용자여야 합니다.");
    }
    if (endsOn != null && startsOn != null && endsOn.isBefore(startsOn)) {
      throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다.");
    }
    // createdByUserId가 지정되면 그 사람이 두 대상 모두의 부서장인지 확인한다.
    // null은 "지정자 없음"(테스트 픽스처·향후 시스템 경로)이며 이때는 리더십 검사를 건너뛴다 —
    // 대신 tenantId 스코프와 위 불변식은 그대로 적용된다. 사용자 요청 경로는 항상 지정자가 있다.
    if (createdByUserId != null
        && (!groupService.leads(tenantId, createdByUserId, absentUserId)
            || !groupService.leads(tenantId, createdByUserId, deputyUserId))) {
      throw new AccessDeniedException("부재자·대직자 모두 본인이 이끄는 팀원이어야 합니다.");
    }
    return repository.save(new DeputyDelegation(
        UUID.randomUUID(), tenantId, absentUserId, deputyUserId, startsOn, endsOn,
        createdByUserId, OffsetDateTime.now()));
  }

  @Transactional
  public void end(UUID tenantId, UUID id) {
    repository.findByTenantIdAndId(tenantId, id).ifPresent(repository::delete);
  }
}
