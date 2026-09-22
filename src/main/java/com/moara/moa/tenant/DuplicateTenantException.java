package com.moara.moa.tenant;

public class DuplicateTenantException extends RuntimeException {
  public DuplicateTenantException(String code) {
    super("A tenant with this code already exists: " + code);
  }
}
