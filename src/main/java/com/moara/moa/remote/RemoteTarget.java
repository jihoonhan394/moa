package com.moara.moa.remote;

import java.util.UUID;

/**
 * 원격 실행 대상. secret은 즉시 사용·폐기하며 로그에 남기지 않는다.
 *
 * <p>기관(tenantId)을 함께 들고 다니는 이유는 호스트 신원 기록 때문이다. 사설망 주소는
 * 기관마다 다른 기계다 — A기관의 {@code 192.168.0.10}과 B기관의 {@code 192.168.0.10}은
 * 남남이라, 기관을 모르면 서로의 호스트 키를 불일치로 판정한다.
 */
public record RemoteTarget(
    UUID tenantId, String host, int port, String username, String secret,
    RemoteProtocol protocol) {

  /** 프로토콜 미지정 시 SSH로 간주. */
  public RemoteTarget(UUID tenantId, String host, int port, String username, String secret) {
    this(tenantId, host, port, username, secret, RemoteProtocol.SSH);
  }
}
