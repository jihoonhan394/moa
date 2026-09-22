package com.moara.moa.remote;

public class RemoteExecutionException extends RuntimeException {
  public RemoteExecutionException(String message) {
    super(message);
  }

  public RemoteExecutionException(String message, Throwable cause) {
    super(message, cause);
  }
}
