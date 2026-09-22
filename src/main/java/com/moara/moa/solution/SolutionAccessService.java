package com.moara.moa.solution;

import com.moara.moa.deputy.DeputyService;
import com.moara.moa.group.UserGroupMember;
import com.moara.moa.group.UserGroupMemberRepository;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 솔루션 사용자 배정/제어 인가. 등록·유지보수는 인프라 관리자(INFRA_MANAGER)가, 운영(제어)은 배정받은
 * 일반 사용자가 한다. 추가로 솔루션에 <b>소유팀(그룹)</b>이 지정되면 그 팀원은 개인 배정 없이도 운영할 수
 * 있다(위키의 '공간→그룹 소유' 패턴을 솔루션으로 일반화). 또한 <b>대직(임시 부재)</b> 기간엔 대직자가
 * 부재자의 제어 권한을 상속한다(합집합). 모든 조회/변경은 현재 기관으로 스코프된다.
 */
@Service
@Transactional(readOnly = true)
public class SolutionAccessService {
  private final SolutionUserAssignmentRepository assignmentRepository;
  private final ManagedSolutionService solutionService;
  private final UserGroupMemberRepository groupMemberRepository;
  private final DeputyService deputyService;

  public SolutionAccessService(
      SolutionUserAssignmentRepository assignmentRepository, ManagedSolutionService solutionService,
      UserGroupMemberRepository groupMemberRepository, DeputyService deputyService) {
    this.assignmentRepository = assignmentRepository;
    this.solutionService = solutionService;
    this.groupMemberRepository = groupMemberRepository;
    this.deputyService = deputyService;
  }

  /** 유효 사용자 집합: 본인 + 대직 중인 부재자(운영 접근 합집합). */
  private Set<UUID> effectiveUsers(UUID tenantId, UUID userId) {
    Set<UUID> users = new HashSet<>();
    users.add(userId);
    users.addAll(deputyService.coveredAbsentUsers(tenantId, userId));
    return users;
  }

  /** 솔루션을 사용자에게 배정한다(멱등 — 이미 있으면 무시). */
  @Transactional
  public void assignUser(UUID tenantId, UUID solutionId, UUID userId) {
    // 존재하는 솔루션인지(그리고 기관 소유인지) 확인.
    solutionService.findById(tenantId, solutionId);
    if (!assignmentRepository.existsByTenantIdAndSolutionIdAndUserId(tenantId, solutionId, userId)) {
      assignmentRepository.save(new SolutionUserAssignment(
          UUID.randomUUID(), tenantId, solutionId, userId, OffsetDateTime.now()));
    }
  }

  @Transactional
  public void unassignUser(UUID tenantId, UUID solutionId, UUID userId) {
    assignmentRepository.deleteByTenantIdAndSolutionIdAndUserId(tenantId, solutionId, userId);
  }

  /** 사용자가 운영할 수 있는 솔루션: 개인 배정 + 소속팀 소유 + 대직 중인 부재자의 것(합집합). */
  public List<ManagedSolution> assignedSolutions(UUID tenantId, UUID userId) {
    Set<UUID> users = effectiveUsers(tenantId, userId);
    Set<UUID> assignedIds = new HashSet<>();
    Set<UUID> groups = new HashSet<>();
    for (UUID uid : users) {
      assignmentRepository.findAllByTenantIdAndUserId(tenantId, uid)
          .forEach(a -> assignedIds.add(a.getSolutionId()));
      groups.addAll(groupIdsOf(tenantId, uid));
    }
    return solutionService.findAll(tenantId).stream()
        .filter(s -> assignedIds.contains(s.getId())
            || (s.getOwnerGroupId() != null && groups.contains(s.getOwnerGroupId())))
        .toList();
  }

  /** 특정 솔루션에 배정된 사용자 식별자. */
  public Set<UUID> assignedUserIds(UUID tenantId, UUID solutionId) {
    return assignmentRepository.findAllByTenantIdAndSolutionId(tenantId, solutionId).stream()
        .map(SolutionUserAssignment::getUserId)
        .collect(Collectors.toSet());
  }

  /** 이 사용자가 해당 솔루션을 제어할 수 있는지: 개인 배정 · 소유팀 소속 · 대직 중인 부재자 권한(합집합). */
  public boolean canControl(UUID tenantId, UUID solutionId, UUID userId) {
    Set<UUID> users = effectiveUsers(tenantId, userId);
    for (UUID uid : users) {
      if (assignmentRepository.existsByTenantIdAndSolutionIdAndUserId(tenantId, solutionId, uid)) {
        return true;
      }
    }
    UUID ownerGroupId = solutionService.findById(tenantId, solutionId).getOwnerGroupId();
    if (ownerGroupId == null) {
      return false;
    }
    return users.stream().anyMatch(uid -> groupIdsOf(tenantId, uid).contains(ownerGroupId));
  }

  private Set<UUID> groupIdsOf(UUID tenantId, UUID userId) {
    return groupMemberRepository.findAllByTenantIdAndUserId(tenantId, userId).stream()
        .map(UserGroupMember::getGroupId)
        .collect(Collectors.toSet());
  }
}
