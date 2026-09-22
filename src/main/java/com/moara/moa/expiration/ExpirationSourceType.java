package com.moara.moa.expiration;

/** 만료 항목의 출처(영향분석 시 무엇을 조회할지 결정). */
public enum ExpirationSourceType {
  INVENTORY,      // sourceId = 인벤토리 항목 id (영향: 보유자 + 보유자의 솔루션)
  ASSET,          // sourceId = 서버/접속 자산 id (영향: 그 자산에 대한 접근)
  ACCESS_GRANT,   // sourceId = 대상 사용자 id (영향: 그 사용자가 접근을 잃음)
  SUBSCRIPTION    // sourceId = 기관 id (영향: 기관 전체)
}
