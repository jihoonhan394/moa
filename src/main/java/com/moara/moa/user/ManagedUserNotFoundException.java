package com.moara.moa.user;

import java.util.UUID;

public class ManagedUserNotFoundException extends RuntimeException {
  public ManagedUserNotFoundException(UUID id) {
    super("Managed user was not found: " + id);
  }
}
