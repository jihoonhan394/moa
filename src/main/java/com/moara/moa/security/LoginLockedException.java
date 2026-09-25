package com.moara.moa.security;

import org.springframework.security.authentication.LockedException;

/**
 * 반복된 실패로 잠긴 상태. 기관 구독 만료로 인한 {@link LockedException}과 구별해야 한다 —
 * 이 예외로 막힌 시도는 <b>실패로 다시 세지 않는다</b>. 세면 잠긴 문을 두드리는 것만으로
 * 잠금이 무한히 연장돼, 시간이 지나도 영영 풀리지 않는다.
 */
public class LoginLockedException extends LockedException {
  public LoginLockedException(String message) {
    super(message);
  }
}
