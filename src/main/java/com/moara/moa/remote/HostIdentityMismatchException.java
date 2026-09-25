package com.moara.moa.remote;

/**
 * 기록된 신원과 다른 상대가 답했다. 서버를 다시 깔았거나 중간에 누가 끼어든 것인데,
 * 코드는 둘을 구별할 수 없으므로 연결하지 않고 사람에게 넘긴다.
 */
public class HostIdentityMismatchException extends RemoteExecutionException {
  public HostIdentityMismatchException(String message) {
    super(message);
  }
}
