package com.moara.moa.solution;

/** 솔루션 제어 결과. output은 원격 명령 출력(상태 등). */
public record ControlResult(ControlAction action, boolean success, String output) {}
