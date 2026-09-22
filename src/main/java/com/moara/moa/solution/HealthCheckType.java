package com.moara.moa.solution;

/** 기동 확인 방식. NONE=검사 안 함, TCP_PORT=포트 열림, COMMAND=명령 종료코드 0. */
public enum HealthCheckType {
  NONE,
  TCP_PORT,
  COMMAND
}
