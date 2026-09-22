package com.moara.moa.solution;

/** 기동 순서 실행 중 한 단계의 결과(화면 표시용). */
public record SequenceStepOutcome(
    int order, String solutionName, ControlAction action, boolean success, boolean skipped, String output) {}
