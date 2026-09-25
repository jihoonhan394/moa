package com.moara.moa.remote;

/**
 * 신원을 확인하는 채널. 같은 호스트라도 채널마다 신원이 다르다 — SSH는 호스트 키,
 * TLS(WinRM HTTPS)는 서버 인증서다.
 */
public enum RemoteChannel {
  SSH,
  TLS
}
