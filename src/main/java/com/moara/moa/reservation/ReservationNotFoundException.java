package com.moara.moa.reservation;

import java.util.UUID;

public class ReservationNotFoundException extends RuntimeException {
  public ReservationNotFoundException(UUID id) {
    super("Reservation was not found: " + id);
  }
}
