package com.moara.moa.onboarding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingTemplateRepository extends JpaRepository<OnboardingTemplate, UUID> {
  List<OnboardingTemplate> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<OnboardingTemplate> findByTenantIdAndId(UUID tenantId, UUID id);
}
