package com.moara.moa.user;

/**
 * 퇴사 회수 중 특정 핸들러가 실패했음을 알린다. 퇴사는 보안 행위라 부분 회수를 허용하지 않으므로
 * 전체 트랜잭션이 롤백되며, <b>어느 모듈에서 막혔는지</b>를 화면·로그로 전달해 복구를 시작할 수 있게 한다.
 */
public class OffboardFailedException extends RuntimeException {
  private final String handlerName;

  public OffboardFailedException(String handlerName, Throwable cause) {
    super("퇴사 회수 실패(" + handlerName + ")", cause);
    this.handlerName = handlerName;
  }

  public String getHandlerName() {
    return handlerName;
  }
}
