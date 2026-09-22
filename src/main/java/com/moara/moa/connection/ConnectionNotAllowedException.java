package com.moara.moa.connection;

/** 접속이 허용되지 않을 때(권한 없음/접속 불가 자산). */
public class ConnectionNotAllowedException extends RuntimeException {
  public ConnectionNotAllowedException(String message) {
    super(message);
  }
}
