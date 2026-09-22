package com.moara.moa.category;

import java.util.UUID;

/** 기관 스코프에서 카테고리를 찾지 못했을 때(잘못된 id 또는 타 기관). */
public class CategoryNotFoundException extends RuntimeException {
  public CategoryNotFoundException(UUID id) {
    super("카테고리를 찾을 수 없습니다: " + id);
  }
}
