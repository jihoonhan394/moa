package com.moara.moa.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moara.moa.tenant.Tenant;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

/** 공유자산 예약: 부킹·중복거부·취소(본인만)·퇴사 일괄 취소 로직 검증. */
@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceTest {
  private static final UUID MOA = Tenant.DEFAULT_TENANT_ID;

  @Autowired private SharedResourceService resourceService;
  @Autowired private ReservationService reservationService;
  @Autowired private ManagedUserService userService;

  @Test
  void bookRejectsOverlapAllowsAdjacent() {
    SharedResource room = room();
    UUID userId = user().getId();
    reservationService.book(MOA, userId, form(room.getId(), at(10, 0), at(11, 0)));

    // 겹치는 시간 → 거부.
    assertThrows(ReservationConflictException.class,
        () -> reservationService.book(MOA, user().getId(), form(room.getId(), at(10, 30), at(11, 30))));
    // 맞닿는 시간(11:00~12:00) → 허용.
    reservationService.book(MOA, user().getId(), form(room.getId(), at(11, 0), at(12, 0)));
  }

  @Test
  void bookRejectsEndBeforeStart() {
    SharedResource room = room();
    assertThrows(ReservationConflictException.class,
        () -> reservationService.book(MOA, user().getId(), form(room.getId(), at(11, 0), at(10, 0))));
  }

  @Test
  void onlyOwnerCanCancel() {
    SharedResource room = room();
    ManagedUser owner = user();
    Reservation rv = reservationService.book(MOA, owner.getId(), form(room.getId(), at(9, 0), at(10, 0)));

    assertThrows(AccessDeniedException.class,
        () -> reservationService.cancel(MOA, rv.getId(), user().getId()));

    reservationService.cancel(MOA, rv.getId(), owner.getId());
    assertEquals(ReservationStatus.CANCELLED,
        reservationService.findMyReservations(MOA, owner.getId()).get(0).getStatus());
  }

  @Test
  void cancelFutureForUserCancelsBooked() {
    SharedResource room = room();
    UUID userId = user().getId();
    reservationService.book(MOA, userId, form(room.getId(), at(13, 0), at(14, 0)));
    reservationService.book(MOA, userId, form(room.getId(), at(14, 0), at(15, 0)));

    long cancelled = reservationService.cancelFutureForUser(MOA, userId);

    assertEquals(2, cancelled);
    assertEquals(0, reservationService.findMyReservations(MOA, userId).stream()
        .filter(Reservation::isBooked).count());
  }

  private SharedResource room() {
    return resourceService.create(MOA, new SharedResourceForm(
        "회의실-" + System.nanoTime(), "회의실", "3층", 6, SharedResourceStatus.ACTIVE, "설명"));
  }

  private ReservationForm form(UUID resourceId, LocalDateTime start, LocalDateTime end) {
    return new ReservationForm(resourceId, start, end, "회의");
  }

  private LocalDateTime at(int hour, int minute) {
    return LocalDateTime.of(2030, 1, 1, hour, minute);
  }

  private ManagedUser user() {
    String username = "u" + System.nanoTime();
    return userService.create(new UserForm(
        username, "홍길동", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
