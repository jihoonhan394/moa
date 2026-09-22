package com.moara.moa.guacamole;

public class GuacamoleException extends RuntimeException {
  public GuacamoleException(String message) {
    super(message);
  }

  public GuacamoleException(String message, Throwable cause) {
    super(message, cause);
  }
}
