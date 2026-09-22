package com.moara.moa.solution;

public class DuplicateSolutionException extends RuntimeException {
  public DuplicateSolutionException(String name) {
    super("같은 서버에 동일 이름의 솔루션이 이미 있습니다: " + name);
  }
}
