package com.moara.moa.solution;

import java.util.UUID;

public class SolutionNotFoundException extends RuntimeException {
  public SolutionNotFoundException(UUID id) {
    super("솔루션을 찾을 수 없습니다: " + id);
  }
}
