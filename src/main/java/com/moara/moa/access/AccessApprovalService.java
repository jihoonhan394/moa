package com.moara.moa.access;

import com.moara.moa.asset.Asset;
import com.moara.moa.asset.AssetService;
import com.moara.moa.audit.AuditLogService;
import com.moara.moa.audit.AuditResult;
import com.moara.moa.connection.ConnectionSessionService;
import com.moara.moa.group.UserGroupMember;
import com.moara.moa.group.UserGroupMemberRepository;
import com.moara.moa.notification.NotificationService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserRole;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접근요청/승인(JIT) 오케스트레이션. 표준 권한이 없는 사용자가 자산 접속을 요청하면 소유팀 리더 또는
 * 관리자가 승인해 기간제 임시 접속을 허용한다(만료=자동 반납, 회수 시 활성 세션 종료).
 * 모든 상태 전이는 tenant로 스코프하고 감사·알림을 남긴다. 승인자는 본인 요청을 승인할 수 없다(maker-checker).
 */
@Service
@Transactional(readOnly = true)
public class AccessApprovalService {
  static final int MIN_DURATION_HOURS = 1;
  static final int MAX_DURATION_HOURS = 720; // 30일

  private static final String TARGET_TYPE = "AccessRequest";

  private final AccessRequestRepository repository;
  private final AssetService assetService;
  private final UserGroupMemberRepository memberRepository;
  private final ManagedUserService userService;
  private final AuditLogService auditLogService;
  private final NotificationService notificationService;
  private final ConnectionSessionService sessionService;
  private final Clock clock;

  @Autowired
  public AccessApprovalService(
      AccessRequestRepository repository, AssetService assetService,
      UserGroupMemberRepository memberRepository, ManagedUserService userService,
      AuditLogService auditLogService, NotificationService notificationService,
      ConnectionSessionService sessionService) {
    this(repository, assetService, memberRepository, userService, auditLogService,
        notificationService, sessionService, Clock.systemUTC());
  }

  AccessApprovalService(
      AccessRequestRepository repository, AssetService assetService,
      UserGroupMemberRepository memberRepository, ManagedUserService userService,
      AuditLogService auditLogService, NotificationService notificationService,
      ConnectionSessionService sessionService, Clock clock) {
    this.repository = repository;
    this.assetService = assetService;
    this.memberRepository = memberRepository;
    this.userService = userService;
    this.auditLogService = auditLogService;
    this.notificationService = notificationService;
    this.sessionService = sessionService;
    this.clock = clock;
  }

  // ── 요청자 ──────────────────────────────────────────────────────

  /** 접근요청 생성(PENDING). 자산 소유(테넌트) 검증 후 [지금 ~ 지금+durationHours] 창으로 만든다. */
  @Transactional
  public AccessRequest request(UUID tenantId, UUID requesterUserId, AccessRequestForm form) {
    Asset asset = assetService.findById(tenantId, form.assetId()); // 교차테넌트=NotFound
    int hours = form.durationHours() == null ? 0 : form.durationHours();
    if (hours < MIN_DURATION_HOURS || hours > MAX_DURATION_HOURS) {
      throw new IllegalArgumentException("요청 기간은 " + MIN_DURATION_HOURS + "~" + MAX_DURATION_HOURS + "시간 이내여야 합니다.");
    }
    boolean alreadyPending = repository
        .findByTenantIdAndRequesterUserIdOrderByCreatedAtDesc(tenantId, requesterUserId).stream()
        .anyMatch(r -> r.getAssetId().equals(form.assetId()) && r.getStatus() == AccessRequestStatus.PENDING);
    if (alreadyPending) {
      throw new IllegalStateException("이미 승인 대기 중인 요청이 있습니다.");
    }
    OffsetDateTime now = OffsetDateTime.now(clock);
    AccessRequest saved = repository.save(AccessRequest.create(
        tenantId, requesterUserId, form.assetId(), form.reason(), now, now.plusHours(hours), now));

    audit(tenantId, requesterUserId, "ACCESS_REQUEST_CREATE", saved.getId(), AuditResult.SUCCESS,
        "접근요청 생성: " + asset.getName() + " (" + hours + "시간)");
    notifyApprovers(tenantId, asset, saved);
    return saved;
  }

  /** 요청자 본인 취소(PENDING만). */
  @Transactional
  public void cancel(UUID tenantId, UUID requestId, UUID requesterUserId) {
    AccessRequest request = load(tenantId, requestId);
    if (!request.getRequesterUserId().equals(requesterUserId)) {
      throw new AccessRequestNotFoundException(requestId); // 남의 요청은 없는 것처럼
    }
    if (request.getStatus() != AccessRequestStatus.PENDING) {
      throw new IllegalStateException("대기 중인 요청만 취소할 수 있습니다.");
    }
    request.cancel(OffsetDateTime.now(clock));
    audit(tenantId, requesterUserId, "ACCESS_REQUEST_CANCEL", requestId, AuditResult.SUCCESS, "요청 취소");
  }

  public List<AccessRequest> myRequests(UUID tenantId, UUID requesterUserId) {
    return repository.findByTenantIdAndRequesterUserIdOrderByCreatedAtDesc(tenantId, requesterUserId);
  }

  // ── 승인자 ──────────────────────────────────────────────────────

  /** 승인: 기간제 접근 허용. 선택적으로 볼트 자격증명(credentialId)을 함께 내준다. */
  @Transactional
  public AccessRequest approve(
      UUID tenantId, UUID requestId, UUID approverUserId, UUID credentialId, String comment) {
    AccessRequest request = load(tenantId, requestId);
    ensurePending(request);
    ensureCanApprove(tenantId, approverUserId, request);
    request.approve(approverUserId, credentialId, comment, OffsetDateTime.now(clock));

    audit(tenantId, approverUserId, "ACCESS_REQUEST_APPROVE", requestId, AuditResult.SUCCESS,
        "접근요청 승인" + (credentialId != null ? " (+자격증명)" : ""));
    notificationService.notify(tenantId, request.getRequesterUserId(), "접근요청 승인됨",
        assetName(tenantId, request) + " 접속이 " + request.getRequestedEndAt().toLocalDateTime() + "까지 허용되었습니다.",
        "/access-requests");
    return request;
  }

  /** 반려. */
  @Transactional
  public void reject(UUID tenantId, UUID requestId, UUID approverUserId, String comment) {
    AccessRequest request = load(tenantId, requestId);
    ensurePending(request);
    ensureCanApprove(tenantId, approverUserId, request);
    request.reject(approverUserId, comment, OffsetDateTime.now(clock));

    audit(tenantId, approverUserId, "ACCESS_REQUEST_REJECT", requestId, AuditResult.SUCCESS, "접근요청 반려");
    notificationService.notify(tenantId, request.getRequesterUserId(), "접근요청 반려됨",
        assetName(tenantId, request) + " 접근요청이 반려되었습니다.", "/access-requests");
  }

  /** 강제 회수(승인됨 → 회수). 요청자의 활성 접속 세션도 종료 기록한다. */
  @Transactional
  public void revoke(UUID tenantId, UUID requestId, UUID approverUserId, String comment) {
    AccessRequest request = load(tenantId, requestId);
    if (request.getStatus() != AccessRequestStatus.APPROVED) {
      throw new IllegalStateException("승인된 요청만 회수할 수 있습니다.");
    }
    ensureCanApprove(tenantId, approverUserId, request);
    request.revoke(approverUserId, comment, OffsetDateTime.now(clock));
    sessionService.closeActiveForUser(tenantId, request.getRequesterUserId());

    audit(tenantId, approverUserId, "ACCESS_REQUEST_REVOKE", requestId, AuditResult.SUCCESS, "접근요청 회수");
    notificationService.notify(tenantId, request.getRequesterUserId(), "접근 권한 회수됨",
        assetName(tenantId, request) + " 임시 접근 권한이 회수되었습니다.", "/access-requests");
  }

  /** 승인 대기 중이며 이 승인자가 승인할 수 있는 요청들. */
  public List<AccessRequest> pendingForApprover(UUID tenantId, UUID approverUserId) {
    return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, AccessRequestStatus.PENDING).stream()
        .filter(r -> canApprove(tenantId, approverUserId, r))
        .toList();
  }

  /** 현재 유효(승인·기간 내)한 요청들 중 이 승인자가 회수할 수 있는 것들. */
  public List<AccessRequest> activeGrantsForApprover(UUID tenantId, UUID approverUserId) {
    return repository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, AccessRequestStatus.APPROVED).stream()
        .filter(r -> canApprove(tenantId, approverUserId, r))
        .toList();
  }

  // ── 런타임 게이트 ────────────────────────────────────────────────

  /** 접속 게이트: 지금 이 사용자가 이 자산에 대해 활성 승인을 가지고 있는가. */
  public boolean hasActiveApproval(UUID tenantId, UUID userId, UUID assetId) {
    return repository.countActiveApprovals(tenantId, userId, assetId, OffsetDateTime.now(clock)) > 0;
  }

  /** 자격증명 주입 접속용: 활성 승인 1건(가장 최근). 없으면 empty. */
  public java.util.Optional<AccessRequest> activeApproval(UUID tenantId, UUID userId, UUID assetId) {
    List<AccessRequest> active = repository.findActiveApprovals(tenantId, userId, assetId, OffsetDateTime.now(clock));
    return active.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(active.get(0));
  }

  // ── 만료 배치 ────────────────────────────────────────────────────

  /** 종료 시각이 지난 승인 건을 EXPIRED로 전이(스케줄러 전용). 반납 처리 건수 반환. */
  @Transactional
  public int expireDue() {
    OffsetDateTime now = OffsetDateTime.now(clock);
    List<AccessRequest> due = repository.findExpiredApprovals(now);
    for (AccessRequest request : due) {
      request.expire();
    }
    return due.size();
  }

  // ── 내부 ────────────────────────────────────────────────────────

  private AccessRequest load(UUID tenantId, UUID requestId) {
    return repository.findByIdAndTenantId(requestId, tenantId)
        .orElseThrow(() -> new AccessRequestNotFoundException(requestId));
  }

  private void ensurePending(AccessRequest request) {
    if (request.getStatus() != AccessRequestStatus.PENDING) {
      throw new IllegalStateException("이미 처리된 요청입니다.");
    }
  }

  private void ensureCanApprove(UUID tenantId, UUID approverUserId, AccessRequest request) {
    if (!canApprove(tenantId, approverUserId, request)) {
      throw new AccessApprovalDeniedException();
    }
  }

  /** 승인 권한: 본인 요청이 아니고(maker-checker), 관리자이거나 자산 소유팀 리더. */
  public boolean canApprove(UUID tenantId, UUID approverUserId, AccessRequest request) {
    if (approverUserId == null || approverUserId.equals(request.getRequesterUserId())) {
      return false;
    }
    ManagedUser approver = userService.findById(approverUserId);
    if (approver == null || !tenantId.equals(approver.getTenantId())) {
      return false;
    }
    if (approver.hasRole(UserRole.TENANT_ADMIN) || approver.hasRole(UserRole.INFRA_MANAGER)) {
      return true;
    }
    UUID ownerGroupId = ownerGroupOf(tenantId, request.getAssetId());
    return ownerGroupId != null && isLeaderOf(tenantId, ownerGroupId, approverUserId);
  }

  private UUID ownerGroupOf(UUID tenantId, UUID assetId) {
    try {
      return assetService.findById(tenantId, assetId).getOwnerGroupId();
    } catch (RuntimeException notFound) {
      return null;
    }
  }

  private boolean isLeaderOf(UUID tenantId, UUID groupId, UUID userId) {
    return memberRepository.findAllByTenantIdAndGroupId(tenantId, groupId).stream()
        .anyMatch(m -> m.isLeader() && m.getUserId().equals(userId));
  }

  private void notifyApprovers(UUID tenantId, Asset asset, AccessRequest request) {
    String requester = userName(request.getRequesterUserId());
    for (UUID approverId : approverUserIds(tenantId, asset, request.getRequesterUserId())) {
      notificationService.notify(tenantId, approverId, "새 접근요청",
          requester + "님이 " + asset.getName() + " 접속을 요청했습니다.", "/access-approvals");
    }
  }

  /** 알림 대상 승인자: 자산 소유팀 리더 ∪ 관리자(요청자 제외). */
  private Set<UUID> approverUserIds(UUID tenantId, Asset asset, UUID excludeUserId) {
    Set<UUID> ids = new LinkedHashSet<>();
    if (asset.getOwnerGroupId() != null) {
      for (UserGroupMember member : memberRepository.findAllByTenantIdAndGroupId(tenantId, asset.getOwnerGroupId())) {
        if (member.isLeader()) {
          ids.add(member.getUserId());
        }
      }
    }
    for (ManagedUser user : userService.findByTenant(tenantId)) {
      if (user.hasRole(UserRole.INFRA_MANAGER) || user.hasRole(UserRole.TENANT_ADMIN)) {
        ids.add(user.getId());
      }
    }
    ids.remove(excludeUserId);
    return ids;
  }

  private String assetName(UUID tenantId, AccessRequest request) {
    try {
      return assetService.findById(tenantId, request.getAssetId()).getName();
    } catch (RuntimeException notFound) {
      return "자산";
    }
  }

  private String userName(UUID userId) {
    ManagedUser user = userService.findById(userId);
    return user == null ? "사용자" : user.getName();
  }

  private void audit(
      UUID tenantId, UUID actorUserId, String action, UUID targetId, AuditResult result, String message) {
    auditLogService.recordTenantAction(tenantId, actorUserId, action, TARGET_TYPE, targetId, result, message);
  }
}
