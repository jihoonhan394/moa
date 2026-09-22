package com.moara.moa.remote;

/** 원격 명령 실행 결과. output은 stdout+stderr 합본. */
public record ExecResult(int exitCode, String output) {
  public boolean success() {
    return exitCode == 0;
  }
}
