package com.moara.moa.remote;

/** 원격 실행 대상. secret은 즉시 사용·폐기하며 로그에 남기지 않는다. */
public record RemoteTarget(String host, int port, String username, String secret, RemoteProtocol protocol) {

  /** 프로토콜 미지정 시 SSH로 간주(기존 호출부 호환). */
  public RemoteTarget(String host, int port, String username, String secret) {
    this(host, port, username, secret, RemoteProtocol.SSH);
  }
}
