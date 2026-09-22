package com.moara.moa.category;

/** 하위 카테고리가 있어 삭제할 수 없을 때. */
public class CategoryInUseException extends RuntimeException {
  public CategoryInUseException(String message) {
    super(message);
  }
}
