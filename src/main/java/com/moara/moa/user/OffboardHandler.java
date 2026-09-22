package com.moara.moa.user;

import java.util.UUID;

/**
 * 퇴사 회수 확장 포인트(플러그인). 각 도메인 모듈이 자기 회수 로직을 구현해 스프링 빈으로 등록하면,
 * {@link UserLifecycleService}가 자동으로 모두 순회한다. 새 모듈 추가/제거 시 코어를 수정하지 않는다.
 * 호출은 offboard 트랜잭션 안에서 이뤄진다.
 */
public interface OffboardHandler {
  OffboardOutcome offboard(UUID tenantId, UUID userId);
}
