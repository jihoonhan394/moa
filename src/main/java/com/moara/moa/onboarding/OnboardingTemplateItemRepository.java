package com.moara.moa.onboarding;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingTemplateItemRepository extends JpaRepository<OnboardingTemplateItem, UUID> {
  List<OnboardingTemplateItem> findAllByTemplateIdOrderBySortOrderAsc(UUID templateId);

  void deleteByTemplateId(UUID templateId);
}
