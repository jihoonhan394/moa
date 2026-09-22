package com.moara.moa.access;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 접근요청 입력. 기간은 시간 단위로 받아 [지금 ~ 지금+시간] 창으로 만든다(요청자 UX 단순화).
 * durationHours 범위·사유 길이는 서버에서 검증한다.
 */
public record AccessRequestForm(
    @NotNull UUID assetId,
    @Size(min = 5, max = 1000, message = "사유는 5자 이상 입력하세요.") String reason,
    @NotNull Integer durationHours) {}
