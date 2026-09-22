package com.moara.moa.expiration;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 통합 만료 대시보드의 한 줄. daysLeft<0=만료됨, 0~N=임박.
 * sourceType/sourceId는 AI 영향분석이 관련 자원(보유자·솔루션 등)을 조회하는 데 쓴다(없으면 null).
 */
public record ExpirationRow(
    String category, String label, String detail, LocalDate expiresOn, long daysLeft,
    ExpirationSourceType sourceType, UUID sourceId) {}
