package com.moara.moa.access;

import java.util.UUID;

/** 접근요청을 찾지 못함(다른 테넌트의 요청 접근 포함 — 교차테넌트는 NotFound로 숨긴다). */
public class AccessRequestNotFoundException extends RuntimeException {
  public AccessRequestNotFoundException(UUID id) {
    super("접근요청을 찾을 수 없습니다: " + id);
  }
}
