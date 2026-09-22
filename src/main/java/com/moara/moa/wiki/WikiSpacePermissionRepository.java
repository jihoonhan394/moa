package com.moara.moa.wiki;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiSpacePermissionRepository extends JpaRepository<WikiSpacePermission, UUID> {
  List<WikiSpacePermission> findAllByTenantId(UUID tenantId);

  List<WikiSpacePermission> findAllByTenantIdAndSpaceId(UUID tenantId, UUID spaceId);

  /** 특정 대상(개인/그룹)에게 직접 부여된 권한 목록('내 공간' 조회용). */
  List<WikiSpacePermission> findAllByTenantIdAndSubjectTypeAndSubjectId(
      UUID tenantId, WikiSubjectType subjectType, UUID subjectId);

  Optional<WikiSpacePermission> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<WikiSpacePermission> findByTenantIdAndSpaceIdAndSubjectTypeAndSubjectId(
      UUID tenantId, UUID spaceId, WikiSubjectType subjectType, UUID subjectId);

  /** 퇴사 회수: 이 사용자에게 직접 부여된 위키 공간 권한 전부 삭제. 삭제 건수 반환. */
  long deleteByTenantIdAndSubjectTypeAndSubjectId(UUID tenantId, WikiSubjectType subjectType, UUID subjectId);
}
