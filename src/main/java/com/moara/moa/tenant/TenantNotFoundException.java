package com.moara.moa.tenant;

public class TenantNotFoundException extends RuntimeException {
  public TenantNotFoundException(String identifier) {
    super("Tenant was not found: " + identifier);
  }
}
