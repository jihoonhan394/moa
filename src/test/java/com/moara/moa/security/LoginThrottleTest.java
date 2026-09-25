package com.moara.moa.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 로그인 잠금의 판단.
 *
 * <p>여기서 틀리면 두 방향으로 나쁘다 — 느슨하면 비밀번호를 무한히 넣어 볼 수 있고, 빡빡하면
 * 정당한 사람이 자기 제품에서 잠긴다. 그래서 "언제 잠기나"만큼 <b>"언제 안 잠기나"</b>도
 * 함께 고정한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoginThrottleTest {
  @Autowired private LoginThrottle throttle;

  private static String ip() {
    return "203.0.113." + (int) (Math.random() * 250 + 1) + "-" + System.nanoTime();
  }

  @Test
  void locksAccountAfterLimit() {
    UUID tenant = UUID.randomUUID();
    String user = "victim-" + System.nanoTime();
    String from = ip();

    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT - 1; i++) {
      throttle.recordFailure(tenant, user, from);
      assertDoesNotThrow(() -> throttle.checkNotLocked(tenant, user, from),
          "임계값 전에 잠겼다 — 정당한 사용자가 오타 몇 번에 갇힌다");
    }

    throttle.recordFailure(tenant, user, from);
    LoginLockedException locked = assertThrows(LoginLockedException.class,
        () -> throttle.checkNotLocked(tenant, user, from));
    assertTrue(locked.getMessage().contains("분 후"), locked.getMessage());
  }

  /** 잠금 안내가 계정의 존재를 알려 주면 안 된다 — 훑는 쪽에 유효 계정 목록을 만들어 준다. */
  @Test
  void lockMessageDoesNotConfirmAccountExists() {
    UUID tenant = UUID.randomUUID();
    String user = "probe-" + System.nanoTime();
    String from = ip();
    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT; i++) {
      throttle.recordFailure(tenant, user, from);
    }

    LoginLockedException locked = assertThrows(LoginLockedException.class,
        () -> throttle.checkNotLocked(tenant, user, from));

    assertTrue(!locked.getMessage().contains(user), "안내에 사용자명이 들어 있다");
    assertTrue(!locked.getMessage().contains("없는"), "계정 존재 여부를 암시한다");
  }

  /** 다른 계정은 영향받지 않는다. 한 사람의 오타가 옆자리를 막으면 안 된다. */
  @Test
  void lockIsScopedToTheAccount() {
    UUID tenant = UUID.randomUUID();
    String locked = "locked-" + System.nanoTime();
    String other = "other-" + System.nanoTime();
    String from = ip();
    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT; i++) {
      throttle.recordFailure(tenant, locked, from);
    }

    assertThrows(LoginLockedException.class, () -> throttle.checkNotLocked(tenant, locked, from));
    assertDoesNotThrow(() -> throttle.checkNotLocked(tenant, other, from));
  }

  /** 같은 아이디라도 기관이 다르면 남남이다(기관마다 아이디가 겹칠 수 있다). */
  @Test
  void lockIsScopedToTheTenant() {
    String user = "shared-name-" + System.nanoTime();
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();
    String from = ip();
    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT; i++) {
      throttle.recordFailure(tenantA, user, from);
    }

    assertThrows(LoginLockedException.class, () -> throttle.checkNotLocked(tenantA, user, from));
    assertDoesNotThrow(() -> throttle.checkNotLocked(tenantB, user, ip()));
  }

  /**
   * 계정을 바꿔 가며 훑는 공격은 계정 카운터에 걸리지 않는다. 출발지 카운터가 그것을 잡는다.
   *
   * <p>임계값이 계정(5)보다 훨씬 높은(20) 이유는 한 사무실이 공인 IP 하나를 쓰는 경우가
   * 흔하기 때문이다 — 같은 5회면 서로 다른 직원의 오타 다섯 번에 사무실 전체가 잠긴다.
   */
  @Test
  void locksSourceThatSweepsManyAccounts() {
    UUID tenant = UUID.randomUUID();
    String from = ip();
    for (int i = 0; i < LoginThrottle.IP_LIMIT; i++) {
      throttle.recordFailure(tenant, "sweep-" + i + "-" + System.nanoTime(), from);
    }

    // 한 번도 틀린 적 없는 계정도 이 출발지에서는 막힌다.
    assertThrows(LoginLockedException.class,
        () -> throttle.checkNotLocked(tenant, "never-tried-" + System.nanoTime(), from));
  }

  /** 계정 임계값을 넘겨도 출발지 임계값에는 한참 못 미친다 — 사무실이 통째로 잠기지 않는다. */
  @Test
  void oneLockedAccountDoesNotLockTheOffice() {
    UUID tenant = UUID.randomUUID();
    String office = ip();
    String clumsy = "clumsy-" + System.nanoTime();
    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT; i++) {
      throttle.recordFailure(tenant, clumsy, office);
    }

    assertThrows(LoginLockedException.class, () -> throttle.checkNotLocked(tenant, clumsy, office));
    assertDoesNotThrow(() -> throttle.checkNotLocked(tenant, "colleague-" + System.nanoTime(), office),
        "동료 한 명의 오타로 같은 사무실이 잠겼다");
  }

  /** 성공하면 그 계정의 실패는 없던 일이 된다. */
  @Test
  void successClearsTheAccountCounter() {
    UUID tenant = UUID.randomUUID();
    String user = "recovering-" + System.nanoTime();
    String from = ip();
    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT; i++) {
      throttle.recordFailure(tenant, user, from);
    }
    assertThrows(LoginLockedException.class, () -> throttle.checkNotLocked(tenant, user, from));

    throttle.clearAccount(tenant, user);

    assertDoesNotThrow(() -> throttle.checkNotLocked(tenant, user, from));
  }

  /**
   * 성공은 <b>출발지</b> 카운터까지 지우지는 않는다. 지우면 공격자가 자기 계정에 한 번
   * 로그인하는 것만으로 자기 IP의 기록을 씻을 수 있다.
   */
  @Test
  void successDoesNotWashTheSourceCounter() {
    UUID tenant = UUID.randomUUID();
    String from = ip();
    for (int i = 0; i < LoginThrottle.IP_LIMIT; i++) {
      throttle.recordFailure(tenant, "sweep-" + i + "-" + System.nanoTime(), from);
    }
    String own = "attacker-own-" + System.nanoTime();

    throttle.clearAccount(tenant, own);

    assertThrows(LoginLockedException.class,
        () -> throttle.checkNotLocked(tenant, "target-" + System.nanoTime(), from),
        "자기 계정 로그인으로 출발지 기록이 씻겼다");
  }

  /** 플랫폼(SYSTEM_ADMIN) 로그인은 소속 기관이 없다. 그래도 잠겨야 한다. */
  @Test
  void platformLoginIsAlsoThrottled() {
    String admin = "platform-admin-" + System.nanoTime();
    String from = ip();
    for (int i = 0; i < LoginThrottle.ACCOUNT_LIMIT; i++) {
      throttle.recordFailure(null, admin, from);
    }

    assertThrows(LoginLockedException.class, () -> throttle.checkNotLocked(null, admin, from));
  }
}
