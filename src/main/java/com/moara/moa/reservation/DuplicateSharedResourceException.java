package com.moara.moa.reservation;

public class DuplicateSharedResourceException extends RuntimeException {
  public DuplicateSharedResourceException(String name) {
    super("Shared resource name already exists in tenant: " + name);
  }
}
