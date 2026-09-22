package com.moara.moa.credential;

public class DuplicateCredentialException extends RuntimeException {
  public DuplicateCredentialException(String name) {
    super("같은 이름의 자격증명이 이미 있습니다: " + name);
  }
}
