package com.moara.moa.solution;

public class DuplicateSolutionSequenceException extends RuntimeException {
  public DuplicateSolutionSequenceException(String name) {
    super("Solution sequence name already exists in tenant: " + name);
  }
}
