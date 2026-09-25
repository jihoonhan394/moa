package com.moara.moa.security;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 반복 실패한 로그인을 잠시 막는다.
 *
 * <p>설계에서 정한 것 네 가지:
 *
 * <p><b>① 푸는 길은 시간뿐이다.</b> 관리자 해제 화면을 두지 않았다 — 그 화면 자체가 로그인
 * 뒤에 있어서, 마지막 관리자가 잠기면 아무도 열 수 없는 문이 된다. 시간이 지나면 저절로
 * 풀리는 쪽이 사람이 갇히지 않는다.
 *
 * <p><b>② 계정과 출발지를 따로 센다.</b> 계정만 세면 계정을 바꿔 가며 훑는 공격(사용자명
 * 스캔)이 카운터에 걸리지 않는다.
 *
 * <p><b>③ 출발지 임계값이 훨씬 높다.</b> 한 사무실이 공인 IP 하나를 쓰는 경우가 흔하다 —
 * 계정과 같은 5회로 두면 서로 다른 직원의 오타 다섯 번에 사무실 전체가 잠긴다. 고객사가
 * 대부분 그런 형태라 이쪽은 넉넉히 둔다. 공격을 막는 주된 방어선은 계정 쪽이고, 출발지
 * 카운터는 "계정을 바꿔 가며 훑는" 경우를 잡는 보조선이다.
 *
 * <p><b>④ 성공은 계정 카운터만 지운다.</b> 출발지 카운터까지 지우면 공격자가 자기 계정에
 * 한 번 로그인하는 것만으로 자기 IP의 기록을 씻을 수 있다.
 */
@Service
public class LoginThrottle {
  /** 한 계정에 허용하는 연속 실패 횟수. */
  public static final int ACCOUNT_LIMIT = 5;

  /** 한 출발지에 허용하는 실패 횟수. 사무실 공용 IP를 고려해 넉넉히 둔다(위 ③). */
  public static final int IP_LIMIT = 20;

  /** 이 시간이 지난 실패는 세지 않는다. 잠긴 뒤 이만큼 지나면 저절로 풀린다. */
  public static final Duration WINDOW = Duration.ofMinutes(15);

  private final LoginFailureRepository repository;

  public LoginThrottle(LoginFailureRepository repository) {
    this.repository = repository;
  }

  /**
   * 잠겨 있으면 막는다. <b>비밀번호를 확인하기 전에</b> 불러야 한다 — 잠긴 뒤에도 매번 대조하면
   * 느린 해시(BCrypt)를 계속 돌리게 되어, 잠금이 오히려 공격자의 도구가 된다.
   */
  @Transactional(readOnly = true)
  public void checkNotLocked(UUID tenantId, String username, String clientIp) {
    OffsetDateTime since = OffsetDateTime.now().minus(WINDOW);
    UUID scope = scope(tenantId);

    if (repository.countByTenantIdAndUsernameIgnoreCaseAndAttemptedAtAfter(scope, username, since)
        >= ACCOUNT_LIMIT) {
      throw new LoginLockedException(lockMessage());
    }
    if (repository.countByClientIpAndAttemptedAtAfter(clientIp, since) >= IP_LIMIT) {
      throw new LoginLockedException(lockMessage());
    }
  }

  /**
   * 실패를 센다.
   *
   * <p><b>새 트랜잭션에서 쓴다.</b> 인증 실패 경로에는 감싸는 트랜잭션이 없거나 롤백되는
   * 트랜잭션이 있을 수 있는데, 그 경우 기록이 함께 사라져 카운터가 영원히 0에 머문다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(UUID tenantId, String username, String clientIp) {
    OffsetDateTime now = OffsetDateTime.now();
    repository.save(new LoginFailure(scope(tenantId), username, clientIp, now));
    // 창을 지난 행은 아무 판단에도 쓰이지 않는다. 별도 배치를 두느니 여기서 치운다 —
    // 실패는 드물고, 실패가 잦을 때 정리가 자주 도는 것은 바라는 바다.
    repository.deleteByAttemptedAtBefore(now.minus(WINDOW).minus(WINDOW));
  }

  /** 로그인에 성공하면 그 계정의 실패는 없던 일이 된다(출발지 카운터는 남는다 — 위 ④). */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void clearAccount(UUID tenantId, String username) {
    repository.deleteByTenantIdAndUsernameIgnoreCase(scope(tenantId), username);
  }

  /**
   * 잠금 안내는 <b>계정이 있는지 없는지를 알려 주지 않는다.</b> "그 계정은 잠겼습니다"는
   * 사용자명이 실재한다는 확인이 되어, 훑는 쪽에 목록을 만들어 준다.
   */
  private static String lockMessage() {
    return "로그인 시도가 너무 많습니다. " + WINDOW.toMinutes() + "분 후에 다시 시도하세요.";
  }

  private static UUID scope(UUID tenantId) {
    return tenantId == null ? LoginFailure.PLATFORM_SCOPE : tenantId;
  }
}
