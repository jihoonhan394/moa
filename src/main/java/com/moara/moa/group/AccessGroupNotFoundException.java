package com.moara.moa.group;

import java.util.UUID;

public class AccessGroupNotFoundException extends RuntimeException {
  public AccessGroupNotFoundException(UUID id) {
    super("Access group was not found: " + id);
  }
}
