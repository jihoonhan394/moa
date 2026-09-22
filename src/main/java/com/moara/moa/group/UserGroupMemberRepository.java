package com.moara.moa.group;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupMemberRepository extends JpaRepository<UserGroupMember, UUID> {
  boolean existsByTenantIdAndUserIdAndGroupId(UUID tenantId, UUID userId, UUID groupId);

  Optional<UserGroupMember> findByTenantIdAndUserIdAndGroupId(UUID tenantId, UUID userId, UUID groupId);

  List<UserGroupMember> findAllByTenantIdAndGroupId(UUID tenantId, UUID groupId);

  long countByTenantIdAndGroupId(UUID tenantId, UUID groupId);

  List<UserGroupMember> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);

  /** 이 사용자가 부서장(leader=true)인 멤버십(=그가 이끄는 그룹들). 위임 권한 판정의 기준. */
  List<UserGroupMember> findAllByTenantIdAndUserIdAndLeaderTrue(UUID tenantId, UUID userId);

  /** 퇴사 회수: 이 사용자의 모든 그룹 멤버십 삭제. 삭제 건수 반환. */
  long deleteByTenantIdAndUserId(UUID tenantId, UUID userId);

  /** 그룹 삭제 시: 이 그룹의 모든 멤버십(부서장 포함) 삭제. */
  long deleteByTenantIdAndGroupId(UUID tenantId, UUID groupId);
}
