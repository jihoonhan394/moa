package com.moara.moa.connection;

import java.util.UUID;

public class ConnectionSessionNotFoundException extends RuntimeException {
  public ConnectionSessionNotFoundException(UUID id) {
    super("Connection session was not found: " + id);
  }
}
