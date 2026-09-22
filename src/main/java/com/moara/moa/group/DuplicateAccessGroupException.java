package com.moara.moa.group;

public class DuplicateAccessGroupException extends RuntimeException {
  public DuplicateAccessGroupException(String name) {
    super("A group with this name already exists in the tenant: " + name);
  }
}
