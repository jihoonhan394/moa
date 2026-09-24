package com.moara.moa.consumable;

import com.moara.moa.notification.NotificationService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소모품 요청 처리. 일반 사용자가 "떨어졌어요"를 남기고 담당자가 접수·거절·보류·완료한다.
 *
 * <p>패턴은 {@code access} 패키지의 요청→승인 흐름을 그대로 따른다 — 새로 만들지 않는다.
 *
 * <h2>왜 수량을 안 받나</h2>
 * 일반 사용자는 몇 박스를 사야 하는지 모른다. 요청은 <b>"떨어졌다"는 신호</b>이고 얼마나 살지는
 * 담당자가 정한다. 입력 칸이 적을수록 실제로 쓴다.
 *
 * <h2>왜 같은 품목 요청을 묶나</h2>
 * 제로 콜라가 떨어지면 다섯 명이 각각 요청한다. 알림이 다섯 번 오면 담당자는 알림을 끈다.
 * 이미 미처리 요청이 있으면 <b>첫 요청에만 알리고</b> 뒤는 인원수만 늘린다.
 */
@Service
@Transactional(readOnly = true)
public class ConsumableRequestService {
  private static final List<ConsumableRequestStatus> OPEN = List.of(
      ConsumableRequestStatus.REQUESTED, ConsumableRequestStatus.ACKNOWLEDGED,
      ConsumableRequestStatus.HELD);

  private final ConsumableRequestRepository repository;
  private final ConsumableService consumableService;
  private final NotificationService notificationService;

  public ConsumableRequestService(
      ConsumableRequestRepository repository, ConsumableService consumableService,
      NotificationService notificationService) {
    this.repository = repository;
    this.consumableService = consumableService;
    this.notificationService = notificationService;
  }

  public List<ConsumableRequest> findAll(UUID tenantId) {
    return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
  }

  public List<ConsumableRequest> findOpen(UUID tenantId) {
    return repository.findByTenantIdAndStatusIn(tenantId, OPEN);
  }

  public List<ConsumableRequest> findMine(UUID tenantId, UUID userId) {
    return repository.findByTenantIdAndRequestedByOrderByCreatedAtDesc(tenantId, userId);
  }

  public ConsumableRequest findById(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id)
        .orElseThrow(() -> new ConsumableItemNotFoundException(id));
  }

  /** 품목별 미처리 요청 수. 목록에서 "3명 요청 중"을 보여 주는 데 쓴다. */
  public Map<UUID, Long> openCountByItem(UUID tenantId) {
    return findOpen(tenantId).stream()
        .collect(Collectors.groupingBy(ConsumableRequest::getItemId, Collectors.counting()));
  }

  /**
   * 요청 등록. 등록된 품목만 고를 수 있고(FK), 이미 미처리 요청이 있으면 담당자에게 다시
   * 알리지 않는다.
   *
   * @return 담당자에게 알려야 하면 true (같은 품목의 첫 요청일 때)
   */
  @Transactional
  public boolean request(UUID tenantId, UUID itemId, UUID userId, String note) {
    ConsumableItem item = consumableService.findById(tenantId, itemId);
    if (!item.isActive()) {
      throw new IllegalArgumentException("지금은 요청할 수 없는 품목입니다.");
    }
    boolean first = repository.findByTenantIdAndItemIdAndStatusIn(tenantId, itemId, OPEN).isEmpty();
    repository.save(new ConsumableRequest(
        UUID.randomUUID(), tenantId, itemId, userId, note, OffsetDateTime.now()));
    return first;
  }

  @Transactional
  public void acknowledge(UUID tenantId, UUID requestId, UUID actorId) {
    ConsumableRequest request = findById(tenantId, requestId);
    request.acknowledge(actorId, OffsetDateTime.now());
    repository.save(request);
    notifyRequester(request, "요청을 접수했습니다", "담당자가 주문을 준비 중입니다.");
  }

  /** 거절·보류는 사유가 필수다 — 이유 없이 닫히면 요청자는 같은 요청을 반복한다. */
  @Transactional
  public void reject(UUID tenantId, UUID requestId, UUID actorId, String reason) {
    requireReason(reason);
    ConsumableRequest request = findById(tenantId, requestId);
    request.reject(actorId, reason, OffsetDateTime.now());
    repository.save(request);
    notifyRequester(request, "요청이 거절되었습니다", reason);
  }

  @Transactional
  public void hold(UUID tenantId, UUID requestId, UUID actorId, String reason, LocalDate reviewOn) {
    requireReason(reason);
    ConsumableRequest request = findById(tenantId, requestId);
    request.hold(actorId, reason, reviewOn, OffsetDateTime.now());
    repository.save(request);
    notifyRequester(request, "요청이 보류되었습니다", reason);
  }

  /**
   * 주문을 등록하면서 이 품목의 미처리 요청을 한꺼번에 완료한다.
   *
   * <p><b>이 연결이 이 기능의 값어치다</b>: 담당자는 주문을 한 번 입력하는데 요청 처리와
   * 주문 이력이 동시에 남는다. 주문 이력이 쌓여야 주기 예측이 먹고 살 데이터가 생긴다.
   *
   * @return 완료 처리된 요청 수
   */
  @Transactional
  public int fulfillByOrder(UUID tenantId, UUID itemId, UUID orderId, UUID actorId) {
    List<ConsumableRequest> open =
        repository.findByTenantIdAndItemIdAndStatusIn(tenantId, itemId, OPEN);
    OffsetDateTime now = OffsetDateTime.now();
    for (ConsumableRequest request : open) {
      request.fulfill(orderId, actorId, now);
      notifyRequester(request, "요청하신 품목을 주문했습니다", "들어오면 받아 가실 수 있습니다.");
    }
    repository.saveAll(open);
    return open.size();
  }

  /** 요청자가 직접 거둬들인다. 아직 담당자가 손대지 않았을 때만. */
  @Transactional
  public void cancel(UUID tenantId, UUID requestId, UUID userId) {
    ConsumableRequest request = findById(tenantId, requestId);
    if (!request.cancelableBy(userId)) {
      throw new AccessDeniedException("이미 처리 중이거나 본인 요청이 아닙니다.");
    }
    repository.delete(request);
  }

  private void requireReason(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("거절·보류는 사유를 적어 주세요.");
    }
  }

  /** 결과를 요청자에게 알린다. 알리지 않으면 요청자는 자기 요청이 어떻게 됐는지 영영 모른다. */
  private void notifyRequester(ConsumableRequest request, String title, String body) {
    ConsumableItem item = consumableService.findById(request.getTenantId(), request.getItemId());
    notificationService.notify(request.getTenantId(), request.getRequestedBy(),
        "[" + item.getName() + "] " + title, body, "/my/consumables");
  }
}
