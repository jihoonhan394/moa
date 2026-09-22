package com.moara.moa.group;

import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserNotFoundException;
import com.moara.moa.user.ManagedUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccessGroupService {
  private final AccessGroupRepository groupRepository;
  private final UserGroupMemberRepository memberRepository;
  private final ManagedUserRepository userRepository;
  private final Clock clock;

  @Autowired
  public AccessGroupService(
      AccessGroupRepository groupRepository,
      UserGroupMemberRepository memberRepository,
      ManagedUserRepository userRepository) {
    this(groupRepository, memberRepository, userRepository, Clock.systemUTC());
  }

  AccessGroupService(
      AccessGroupRepository groupRepository,
      UserGroupMemberRepository memberRepository,
      ManagedUserRepository userRepository,
      Clock clock) {
    this.groupRepository = groupRepository;
    this.memberRepository = memberRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  // --- 그룹 CRUD (테넌트 스코프) ---

  public List<AccessGroup> findAll(UUID tenantId) {
    return groupRepository.findAllByTenantIdOrderByNameAsc(tenantId);
  }

  /** 다른 테넌트의 그룹 id는 존재를 노출하지 않고 NotFound로 처리한다(격리). */
  public AccessGroup findById(UUID tenantId, UUID id) {
    return groupRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new AccessGroupNotFoundException(id));
  }

  @Transactional
  public AccessGroup create(UUID tenantId, AccessGroupForm form) {
    String name = form.name().trim();
    if (groupRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
      throw new DuplicateAccessGroupException(name);
    }
    if (form.parentId() != null) {
      findById(tenantId, form.parentId()); // 상위 그룹 존재·소유 검증(신규 노드라 순환 불가)
    }
    return groupRepository.save(
        new AccessGroup(UUID.randomUUID(), tenantId, form, OffsetDateTime.now(clock)));
  }

  /**
   * 조직 트리를 전위순회(DFS)로 평탄화해 depth와 함께 돌려준다. 순환/유실 참조가 있어도 무한루프
   * 없이(방문 집합) 모든 그룹을 한 번씩 노출한다 — 도달 못한 그룹은 최상위로 덧붙인다.
   */
  public List<GroupNode> tree(UUID tenantId) {
    List<AccessGroup> all = groupRepository.findAllByTenantIdOrderByNameAsc(tenantId);
    java.util.Map<UUID, List<AccessGroup>> byParent = new java.util.LinkedHashMap<>();
    for (AccessGroup group : all) {
      byParent.computeIfAbsent(group.getParentId(), key -> new java.util.ArrayList<>()).add(group);
    }
    List<GroupNode> out = new java.util.ArrayList<>();
    java.util.Set<UUID> visited = new java.util.HashSet<>();
    appendChildren(tenantId, null, 0, byParent, visited, out);
    // 유실 참조(상위가 없거나 다른 트리)로 도달 못한 그룹은 최상위로.
    for (AccessGroup group : all) {
      if (!visited.contains(group.getId())) {
        appendSubtree(tenantId, group, 0, byParent, visited, out);
      }
    }
    return out;
  }

  private void appendChildren(
      UUID tenantId, UUID parentId, int depth, java.util.Map<UUID, List<AccessGroup>> byParent,
      java.util.Set<UUID> visited, List<GroupNode> out) {
    for (AccessGroup child : byParent.getOrDefault(parentId, List.of())) {
      appendSubtree(tenantId, child, depth, byParent, visited, out);
    }
  }

  private void appendSubtree(
      UUID tenantId, AccessGroup group, int depth, java.util.Map<UUID, List<AccessGroup>> byParent,
      java.util.Set<UUID> visited, List<GroupNode> out) {
    if (!visited.add(group.getId())) {
      return; // 순환 방지
    }
    long members = memberRepository.countByTenantIdAndGroupId(tenantId, group.getId());
    boolean hasChildren = byParent.containsKey(group.getId());
    out.add(new GroupNode(group, depth, members, hasChildren));
    appendChildren(tenantId, group.getId(), depth + 1, byParent, visited, out);
  }

  /**
   * 그룹의 상위를 이동한다. 자기 자신·후손을 상위로 지정하면 순환이므로 거부한다.
   * newParentId=null이면 최상위로 올린다.
   */
  @Transactional
  public void move(UUID tenantId, UUID id, UUID newParentId) {
    AccessGroup group = findById(tenantId, id);
    if (newParentId != null) {
      if (newParentId.equals(id)) {
        throw new IllegalArgumentException("자기 자신을 상위로 지정할 수 없습니다.");
      }
      AccessGroup parent = findById(tenantId, newParentId); // 존재·소유 검증
      UUID cursor = parent.getParentId();
      while (cursor != null) {
        if (cursor.equals(id)) {
          throw new IllegalArgumentException("하위 그룹을 상위로 지정할 수 없습니다(순환).");
        }
        cursor = findById(tenantId, cursor).getParentId();
      }
    }
    group.changeParent(newParentId, OffsetDateTime.now(clock));
  }

  // --- 부서장(위임) 권한 ---
  // 재사용 기준점: "이 사용자가 부서장인가", "그가 이끄는 부서·팀원은 누구인가", "대상 사용자에 대해
  // 위임 권한이 있는가". 온보딩 적용·자산 위임 등 여러 모듈이 이 판정을 공유한다(권한 로직 한 곳).

  /** 이 사용자가 하나 이상의 그룹 부서장인가. */
  public boolean isDepartmentLeader(UUID tenantId, UUID userId) {
    return userId != null
        && !memberRepository.findAllByTenantIdAndUserIdAndLeaderTrue(tenantId, userId).isEmpty();
  }

  /** 이 사용자가 부서장으로 있는 그룹들. */
  public List<AccessGroup> groupsLedBy(UUID tenantId, UUID userId) {
    if (userId == null) {
      return List.of();
    }
    return memberRepository.findAllByTenantIdAndUserIdAndLeaderTrue(tenantId, userId).stream()
        .map(membership -> groupRepository.findByIdAndTenantId(membership.getGroupId(), tenantId).orElse(null))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  /** 부서장이 이끄는 그룹들의 팀원 식별자(본인 제외). 위임 대상 후보. */
  public java.util.Set<UUID> teamMemberIds(UUID tenantId, UUID leaderUserId) {
    java.util.Set<UUID> ids = new java.util.LinkedHashSet<>();
    for (AccessGroup group : groupsLedBy(tenantId, leaderUserId)) {
      for (UserGroupMember member : memberRepository.findAllByTenantIdAndGroupId(tenantId, group.getId())) {
        ids.add(member.getUserId());
      }
    }
    ids.remove(leaderUserId);
    return ids;
  }

  /** 부서장이 대상 사용자에 대해 위임 권한을 갖는가(대상이 그가 이끄는 그룹의 팀원인가). */
  public boolean leads(UUID tenantId, UUID leaderUserId, UUID targetUserId) {
    return targetUserId != null && teamMemberIds(tenantId, leaderUserId).contains(targetUserId);
  }

  /** 트리 표시용 노드(그룹 + 깊이 + 멤버 수 + 하위 존재 여부). */
  public record GroupNode(AccessGroup group, int depth, long memberCount, boolean hasChildren) {}

  @Transactional
  public AccessGroup update(UUID tenantId, UUID id, AccessGroupForm form) {
    AccessGroup group = findById(tenantId, id);
    String name = form.name().trim();
    if (groupRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name, id)) {
      throw new DuplicateAccessGroupException(name);
    }
    group.update(form, OffsetDateTime.now(clock));
    return group;
  }

  @Transactional
  public void disable(UUID tenantId, UUID id) {
    findById(tenantId, id).disable(OffsetDateTime.now(clock));
  }

  /**
   * 그룹을 영구 삭제한다. 하위 그룹은 상위(조부모/최상위)로 승격해 트리를 유지하고, 이 그룹의 멤버십(부서장 포함)을
   * 제거한 뒤 삭제한다. 접근 권한(묶음)이 부착돼 있으면 FK로 삭제가 막히며, 이때 flush로 즉시
   * {@link org.springframework.dao.DataIntegrityViolationException}를 발생시켜 호출부에서 안내로 전환한다.
   */
  @Transactional
  public void delete(UUID tenantId, UUID id) {
    AccessGroup group = findById(tenantId, id);
    OffsetDateTime now = OffsetDateTime.now(clock);
    for (AccessGroup child : groupRepository.findAllByTenantIdOrderByNameAsc(tenantId)) {
      if (id.equals(child.getParentId())) {
        child.changeParent(group.getParentId(), now); // 하위를 상위로 승격
      }
    }
    memberRepository.deleteByTenantIdAndGroupId(tenantId, id);
    groupRepository.delete(group);
    groupRepository.flush();
  }

  // --- 사용자-그룹 매핑 ---

  /**
   * 사용자를 그룹에 추가한다. 그룹과 사용자가 모두 해당 테넌트에 속해야 하며(교차 테넌트 차단),
   * 이미 존재하는 매핑은 그대로 반환한다(멱등).
   */
  @Transactional
  public UserGroupMember addMember(UUID tenantId, UUID groupId, UUID userId) {
    AccessGroup group = findById(tenantId, groupId); // 그룹 소유권 검증
    ManagedUser user = userRepository.findById(userId)
        .orElseThrow(() -> new ManagedUserNotFoundException(userId));
    if (!tenantId.equals(user.getTenantId())) {
      throw new CrossTenantMembershipException(
          "User " + userId + " does not belong to tenant " + tenantId);
    }
    return memberRepository.findByTenantIdAndUserIdAndGroupId(tenantId, userId, group.getId())
        .orElseGet(() -> memberRepository.save(new UserGroupMember(
            UUID.randomUUID(), tenantId, userId, group.getId(), OffsetDateTime.now(clock))));
  }

  @Transactional
  public void removeMember(UUID tenantId, UUID groupId, UUID userId) {
    findById(tenantId, groupId); // 그룹 소유권 검증
    memberRepository.findByTenantIdAndUserIdAndGroupId(tenantId, userId, groupId)
        .ifPresent(memberRepository::delete);
  }

  public List<UserGroupMember> findMembers(UUID tenantId, UUID groupId) {
    findById(tenantId, groupId); // 그룹 소유권 검증
    return memberRepository.findAllByTenantIdAndGroupId(tenantId, groupId);
  }

  /**
   * 그룹 멤버의 부서장 지정/해제. 멤버가 아니면 무시(멱등). 대상은 반드시 해당 그룹의 멤버여야 한다.
   */
  @Transactional
  public void setLeader(UUID tenantId, UUID groupId, UUID userId, boolean leader) {
    findById(tenantId, groupId); // 그룹 소유권 검증
    memberRepository.findByTenantIdAndUserIdAndGroupId(tenantId, userId, groupId)
        .ifPresent(member -> member.setLeader(leader));
  }

  public List<UserGroupMember> findGroupsOfUser(UUID tenantId, UUID userId) {
    return memberRepository.findAllByTenantIdAndUserId(tenantId, userId);
  }
}
