package com.moara.moa.reservation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/** 예약(부킹) 폼. 시간은 datetime-local(벽시계) 입력. */
public record ReservationForm(
    @NotNull UUID resourceId,
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAt,
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endsAt,
    @Size(max = 300) String purpose) {}
