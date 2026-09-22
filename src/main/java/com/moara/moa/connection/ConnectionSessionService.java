package com.moara.moa.connection;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접속 세션/이벤트 이력 기록. 상태 전이마다 이벤트를 같은 트랜잭션으로 append 한다(감사 우선 —
 * 이력 기록이 실패하면 트랜잭션이 롤백되어 상태 변경도 취소된다). 실제 접속 오케스트레이션과
 * 접속 전 권한 검사는 T10에서 이 서비스를 호출해 구성한다. 이벤트는 append-only(수정/삭제 없음).
 */
@Service
@Transactional(readOnly = true)
public class ConnectionSessionService {
  private final ConnectionSessionRepository sessionRepository;
  private final ConnectionEventRepository eventRepository;
  private final Clock clock;

  @Autowired
  public ConnectionSessionService(
      ConnectionSessionRepository sessionRepository, ConnectionEventRepository eventRepository) {
    this(sessionRepository, eventRepository, Clock.systemUTC());
  }

  ConnectionSessionService(
      ConnectionSessionRepository sessionRepository,
      ConnectionEventRepository eventRepository,
      Clock clock) {
    this.sessionRepository = sessionRepository;
    this.eventRepository = eventRepository;
    this.clock = clock;
  }

  /** 접속 시도를 시작한다(status=ATTEMPTING). ATTEMPT 이벤트를 함께 기록한다. */
  @Transactional
  public ConnectionSession openSession(
      UUID tenantId, UUID userId, UUID assetId, SessionProtocol protocol,
      String sessionId, String clientIp) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    ConnectionSession session = sessionRepository.save(new ConnectionSession(
        UUID.randomUUID(), tenantId, sessionId, userId, assetId, protocol, clientIp, now));
    recordEvent(session, ConnectionEventType.ATTEMPT, ConnectionEventResult.SUCCESS, "connection attempt", now);
    return session;
  }

  @Transactional
  public ConnectionSession markConnected(UUID tenantId, UUID id) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    ConnectionSession session = getOwned(tenantId, id);
    session.markConnected(now);
    recordEvent(session, ConnectionEventType.SUCCESS, ConnectionEventResult.SUCCESS, "connected", now);
    return session;
  }

  /** 접속 실패(권한 거부/인증 실패/네트워크 등). failureCode/message에 자격증명값을 담지 않는다. */
  @Transactional
  public ConnectionSession markFailed(UUID tenantId, UUID id, String failureCode, String message) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    ConnectionSession session = getOwned(tenantId, id);
    session.markFailed(failureCode, now);
    recordEvent(session, ConnectionEventType.FAILURE, ConnectionEventResult.FAILURE, message, now);
    return session;
  }

  @Transactional
  public ConnectionSession markClosed(UUID tenantId, UUID id) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    ConnectionSession session = getOwned(tenantId, id);
    session.markClosed(now);
    recordEvent(session, ConnectionEventType.CLOSED, ConnectionEventResult.SUCCESS, "closed", now);
    return session;
  }

  public ConnectionSession findById(UUID tenantId, UUID id) {
    return getOwned(tenantId, id);
  }

  /**
   * 퇴사/차단용: 사용자의 활성(CONNECTED) 세션을 모두 CLOSED로 기록한다. 닫은 수 반환.
   * 주의: 이미 열린 Guacamole 터널을 실제로 절단하지는 못한다(게이트웨이 관리자 연동 필요 — 로드맵).
   * 접근 권한이 회수되므로 재접속·신규 세션은 차단되며, 이 기록은 감사·현황 반영용이다.
   */
  @Transactional
  public int closeActiveForUser(UUID tenantId, UUID userId) {
    OffsetDateTime now = OffsetDateTime.now(clock);
    List<ConnectionSession> active = sessionRepository.findAllByTenantIdAndUserIdAndStatus(
        tenantId, userId, ConnectionStatus.CONNECTED);
    for (ConnectionSession session : active) {
      session.markClosed(now);
      recordEvent(session, ConnectionEventType.CLOSED, ConnectionEventResult.SUCCESS, "closed on offboard", now);
    }
    return active.size();
  }

  public List<ConnectionSession> findHistory(UUID tenantId) {
    return sessionRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
  }

  public List<ConnectionSession> findHistoryForUser(UUID tenantId, UUID userId) {
    return sessionRepository.findAllByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId);
  }

  public List<ConnectionEvent> findEvents(UUID tenantId, UUID sessionId) {
    getOwned(tenantId, sessionId); // 세션 소유권 검증
    return eventRepository.findAllByTenantIdAndConnectionSessionIdOrderByCreatedAtAsc(tenantId, sessionId);
  }

  private ConnectionSession getOwned(UUID tenantId, UUID id) {
    return sessionRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ConnectionSessionNotFoundException(id));
  }

  private void recordEvent(
      ConnectionSession session, ConnectionEventType type, ConnectionEventResult result,
      String message, OffsetDateTime now) {
    eventRepository.save(new ConnectionEvent(
        UUID.randomUUID(), session.getTenantId(), session.getId(), type, result, message, now));
  }
}
