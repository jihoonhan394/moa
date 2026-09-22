package com.moara.moa.solution;

import java.util.UUID;

public class SolutionSequenceNotFoundException extends RuntimeException {
  public SolutionSequenceNotFoundException(UUID id) {
    super("Solution sequence was not found: " + id);
  }
}
