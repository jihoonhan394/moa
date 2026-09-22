package com.moara.moa.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/** 점검 일정 등록 폼(대상은 컨트롤러가 targetType+id로 받는다). 시작은 필수, 종료는 선택. */
public record MaintenanceWindowForm(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 1000) String reason,
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt) {}
