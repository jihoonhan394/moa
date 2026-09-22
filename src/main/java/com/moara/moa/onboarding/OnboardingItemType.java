package com.moara.moa.onboarding;

/**
 * 온보딩 템플릿 항목의 종류. 두 부류로 나뉜다.
 * <ul>
 *   <li><b>즉시 실행(프로비저닝)</b>: {@code ASSIGN_SOLUTION}(솔루션 배정), {@code GRANT_WIKI_SPACE}(위키 공간 열람 부여).
 *       템플릿 적용 시 바로 권한이 부여된다.</li>
 *   <li><b>체크리스트</b>: {@code TASK}(할 일), {@code ACK_DOC}(필독 문서 읽고 동의).
 *       템플릿 적용 시 신입 개인 체크리스트로 복사된다.</li>
 * </ul>
 * 물리 자산(노트북·차량 등 개체)은 여기서 다루지 않는다 — 경영지원팀이 인벤토리에서 개체를 직접 배정하고,
 * 신입은 '내 워크스페이스'에서 배정 결과를 읽기만 한다(관리 책임과 소비 뷰의 분리).
 */
public enum OnboardingItemType {
  ASSIGN_SOLUTION("솔루션 배정"),
  GRANT_WIKI_SPACE("위키 공간 열람"),
  TASK("할 일"),
  ACK_DOC("필독 문서");

  private final String label;

  OnboardingItemType(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  /** 신입 개인 체크리스트로 복사되는 항목인가(TASK/ACK_DOC). */
  public boolean isChecklist() {
    return this == TASK || this == ACK_DOC;
  }
}
