package com.moara.moa.onboarding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, UUID> {
  List<OnboardingTask> findAllByTenantIdAndUserIdOrderByCreatedAtAsc(UUID tenantId, UUID userId);

  Optional<OnboardingTask> findByTenantIdAndIdAndUserId(UUID tenantId, UUID id, UUID userId);

  long countByTenantIdAndUserId(UUID tenantId, UUID userId);

  long countByTenantIdAndUserIdAndDoneTrue(UUID tenantId, UUID userId);

  /** 퇴사 회수: 이 사용자의 온보딩 체크리스트 전부 삭제. 삭제 건수 반환. */
  long deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
