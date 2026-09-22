package com.moara.moa.reservation;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공유자산 예약. 일반 사용자가 시간대로 예약하며 같은 자산의 시간 중복은 거부한다(BOOKED 기준).
 * 취소는 예약자 본인만. 퇴사 시 향후 예약은 시스템이 일괄 취소한다.
 */
@Service
@Transactional(readOnly = true)
public class ReservationService {
  private final ReservationRepository reservationRepository;
  private final SharedResourceService resourceService;

  public ReservationService(
      ReservationRepository reservationRepository, SharedResourceService resourceService) {
    this.reservationRepository = reservationRepository;
    this.resourceService = resourceService;
  }

  /** 예약 가능한(ACTIVE) 공유자산 목록. */
  public List<SharedResource> bookableResources(UUID tenantId) {
    return resourceService.findAll(tenantId).stream().filter(SharedResource::isBookable).toList();
  }

  @Transactional
  public Reservation book(UUID tenantId, UUID userId, ReservationForm form) {
    SharedResource resource = resourceService.findById(tenantId, form.resourceId());
    if (!resource.isBookable()) {
      throw new ReservationConflictException("사용 중지된 자산은 예약할 수 없습니다.");
    }
    if (form.startsAt() == null || form.endsAt() == null || !form.endsAt().isAfter(form.startsAt())) {
      throw new ReservationConflictException("종료 시각이 시작 시각보다 늦어야 합니다.");
    }
    if (reservationRepository.countOverlapping(tenantId, form.resourceId(), form.startsAt(), form.endsAt()) > 0) {
      throw new ReservationConflictException("이미 예약된 시간대입니다.");
    }
    return reservationRepository.save(new Reservation(
        UUID.randomUUID(), tenantId, form.resourceId(), userId,
        form.startsAt(), form.endsAt(), form.purpose(), OffsetDateTime.now()));
  }

  /** 예약 취소(본인만). */
  @Transactional
  public void cancel(UUID tenantId, UUID id, UUID requesterUserId) {
    Reservation reservation = reservationRepository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new ReservationNotFoundException(id));
    if (!reservation.getUserId().equals(requesterUserId)) {
      throw new AccessDeniedException("본인 예약만 취소할 수 있습니다.");
    }
    reservation.cancel();
    reservationRepository.save(reservation);
  }

  /** 기관 전체의 다가오는(종료 미도래) 예약 수 — 대시보드용. */
  public long upcomingCount(UUID tenantId) {
    return reservationRepository.countByTenantIdAndStatusAndEndsAtAfter(
        tenantId, ReservationStatus.BOOKED, LocalDateTime.now());
  }

  public List<Reservation> findMyReservations(UUID tenantId, UUID userId) {
    return reservationRepository.findAllByTenantIdAndUserIdOrderByStartsAtDesc(tenantId, userId);
  }

  /** 특정 자산의 다가오는 예약(현황 표시용). */
  public List<Reservation> upcomingForResource(UUID tenantId, UUID resourceId) {
    return reservationRepository.findAllByTenantIdAndResourceIdAndStatusAndEndsAtAfterOrderByStartsAtAsc(
        tenantId, resourceId, ReservationStatus.BOOKED, LocalDateTime.now());
  }

  /** 퇴사 회수: 사용자의 향후(진행중·예정) 예약을 모두 취소. 취소 건수 반환. */
  @Transactional
  public long cancelFutureForUser(UUID tenantId, UUID userId) {
    List<Reservation> future = reservationRepository.findAllByTenantIdAndUserIdAndStatusAndEndsAtAfter(
        tenantId, userId, ReservationStatus.BOOKED, LocalDateTime.now());
    for (Reservation reservation : future) {
      reservation.cancel();
    }
    reservationRepository.saveAll(future);
    return future.size();
  }
}
