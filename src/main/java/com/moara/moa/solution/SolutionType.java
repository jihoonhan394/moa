package com.moara.moa.solution;

/**
 * 제어 대상 유형. DB(MySQL/PostgreSQL 등)는 OS 서비스로 커버(WINDOWS_SERVICE/LINUX_DAEMON),
 * Docker 데몬도 서비스이지만 개별 컨테이너는 {@code docker start/stop}이라 별도 유형.
 * Oracle처럼 스크립트 제어가 필요한 것은 후속 CUSTOM_COMMAND(명령 직접 지정)로 확장.
 */
public enum SolutionType {
  WINDOWS_SERVICE,
  WINDOWS_EXE,
  LINUX_DAEMON,
  DOCKER_CONTAINER,
  /** 명령 직접 지정(start/stop/status). Oracle 스크립트, 임의 프로세스 등. */
  CUSTOM_COMMAND
}
