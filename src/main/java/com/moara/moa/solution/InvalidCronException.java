package com.moara.moa.solution;

/** 잘못된 cron 식. 컨트롤러가 사용자 메시지로 변환한다. */
public class InvalidCronException extends RuntimeException {
  public InvalidCronException(String cron) {
    super("Invalid cron expression: " + cron);
  }
}
