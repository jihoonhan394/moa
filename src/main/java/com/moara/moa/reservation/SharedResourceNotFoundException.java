package com.moara.moa.reservation;

import java.util.UUID;

public class SharedResourceNotFoundException extends RuntimeException {
  public SharedResourceNotFoundException(UUID id) {
    super("Shared resource was not found: " + id);
  }
}
