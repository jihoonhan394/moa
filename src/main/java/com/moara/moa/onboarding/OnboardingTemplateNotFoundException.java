package com.moara.moa.onboarding;

import java.util.UUID;

/** 온보딩 템플릿을 현재 기관에서 찾지 못함(교차기관 접근·삭제된 템플릿). */
public class OnboardingTemplateNotFoundException extends RuntimeException {
  public OnboardingTemplateNotFoundException(UUID id) {
    super("Onboarding template not found: " + id);
  }
}
