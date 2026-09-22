package com.moara.moa.onboarding;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자의 온보딩 체크리스트를 정리한다(입사의 거울). */
@Component
public class OnboardingTaskOffboardHandler implements OffboardHandler {
  private final OnboardingService onboardingService;

  public OnboardingTaskOffboardHandler(OnboardingService onboardingService) {
    this.onboardingService = onboardingService;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("온보딩 체크리스트", onboardingService.removeTasksOf(tenantId, userId));
  }
}
