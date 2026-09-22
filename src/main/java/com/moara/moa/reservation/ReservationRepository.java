package com.moara.moa.reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
  Optional<Reservation> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Reservation> findAllByTenantIdAndUserIdOrderByStartsAtDesc(UUID tenantId, UUID userId);

  List<Reservation> findAllByTenantIdAndResourceIdAndStatusAndEndsAtAfterOrderByStartsAtAsc(
      UUID tenantId, UUID resourceId, ReservationStatus status, LocalDateTime after);

  List<Reservation> findAllByTenantIdAndUserIdAndStatusAndEndsAtAfter(
      UUID tenantId, UUID userId, ReservationStatus status, LocalDateTime after);

  long countByTenantIdAndStatusAndEndsAtAfter(UUID tenantId, ReservationStatus status, LocalDateTime after);

  /** 같은 자산에서 [start, end) 구간과 겹치는 예약 수(BOOKED만). 0이면 예약 가능. */
  @Query("SELECT COUNT(r) FROM Reservation r WHERE r.tenantId = :tenantId AND r.resourceId = :resourceId "
      + "AND r.status = com.moara.moa.reservation.ReservationStatus.BOOKED "
      + "AND r.startsAt < :end AND r.endsAt > :start")
  long countOverlapping(
      @Param("tenantId") UUID tenantId, @Param("resourceId") UUID resourceId,
      @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
