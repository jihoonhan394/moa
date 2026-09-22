package com.moara.moa.guacamole;

/** MOA → Guacamole 연동 경계. 권한 검사를 통과한 접속에 대해 1회성 세션을 만든다. */
public interface GuacamoleClient {
  boolean isEnabled();

  GuacamoleLaunch createSession(GuacamoleConnectionRequest request);
}
