package com.moara.moa.connection;

/** 세션 상태 전이: 시도 → 연결/실패 → 종료. */
public enum ConnectionStatus {
  ATTEMPTING,
  CONNECTED,
  FAILED,
  CLOSED
}
