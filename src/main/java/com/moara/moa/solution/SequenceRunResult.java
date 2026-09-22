package com.moara.moa.solution;

import java.util.List;

/** 기동 순서 실행 결과. reverse=false 정방향(각 단계 동작 그대로), true 역방향(반대 동작을 역순으로). */
public record SequenceRunResult(boolean reverse, boolean overallSuccess, List<SequenceStepOutcome> steps) {}
