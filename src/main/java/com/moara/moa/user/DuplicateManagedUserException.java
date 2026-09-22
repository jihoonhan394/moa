package com.moara.moa.user;

public class DuplicateManagedUserException extends RuntimeException {
  public DuplicateManagedUserException(String field) {
    super("A user with this " + field + " already exists.");
  }
}
