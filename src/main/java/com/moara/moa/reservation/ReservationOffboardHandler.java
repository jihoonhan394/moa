package com.moara.moa.reservation;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자의 향후 공유자산 예약 일괄 취소. */
@Component
public class ReservationOffboardHandler implements OffboardHandler {
  private final ReservationService reservationService;

  public ReservationOffboardHandler(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("예약", reservationService.cancelFutureForUser(tenantId, userId));
  }
}
