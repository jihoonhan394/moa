package com.moara.moa.category;

/** 같은 (기관·도메인)에 동일 이름 카테고리가 이미 있을 때. */
public class DuplicateCategoryException extends RuntimeException {
  public DuplicateCategoryException(String name) {
    super("이미 있는 카테고리: " + name);
  }
}
