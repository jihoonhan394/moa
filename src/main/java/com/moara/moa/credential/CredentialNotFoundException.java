package com.moara.moa.credential;

import java.util.UUID;

public class CredentialNotFoundException extends RuntimeException {
  public CredentialNotFoundException(UUID id) {
    super("자격증명을 찾을 수 없습니다: " + id);
  }
}
