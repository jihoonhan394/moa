package com.moara.moa.expiration;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 통합 만료 대시보드의 한 줄. daysLeft&lt;0=만료됨, 0~N=임박.
 *
 * <p>sourceType/sourceId는 AI 영향분석이 관련 자원(보유자·솔루션 등)을 조회하는 데 쓴다(없으면 null).
 *
 * <p>link는 그 항목을 <b>실제로 손볼 수 있는 화면</b>의 경로다. 목록이 임박 항목을 보여 주면서
 * 정작 거기서 아무것도 할 수 없으면(대상을 목록에서 다시 찾아 들어가야 하면) 화면의 값어치가
 * 절반으로 준다. 열 수 없는 항목(구독 만기 등)은 null이며, 역할에 따른 노출 여부는 화면이 정한다
 * — 링크가 있다고 누구나 열 수 있는 것은 아니기 때문이다.
 */
public record ExpirationRow(
    String category, String label, String detail, LocalDate expiresOn, long daysLeft,
    ExpirationSourceType sourceType, UUID sourceId, String link) {}
