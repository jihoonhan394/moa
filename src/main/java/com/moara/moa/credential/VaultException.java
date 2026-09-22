package com.moara.moa.credential;

/** 볼트 암호화/복호화/키 설정 오류. 메시지에 비밀 평문을 절대 담지 않는다. */
public class VaultException extends RuntimeException {
  public VaultException(String message) {
    super(message);
  }

  public VaultException(String message, Throwable cause) {
    super(message, cause);
  }
}
