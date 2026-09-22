package com.moara.moa.solution;

import java.util.UUID;

/** 화면 표시용 단계 뷰(순서·솔루션 이름·유형·동작·대기초·상태확인 여부). */
public record SolutionSequenceStepView(
    UUID stepId, int order, String solutionName, String solutionType,
    ControlAction action, int waitSeconds, boolean verifyAfterStart) {}
