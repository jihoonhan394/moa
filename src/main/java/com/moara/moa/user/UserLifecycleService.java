package com.moara.moa.user;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 입·퇴사 라이프사이클. 퇴사 처리 시 등록된 모든 {@link OffboardHandler}(모듈별 회수 플러그인)를
 * 순회해 접근·자산·세션·예약·위키 권한 등을 회수하고 상태를 OFFBOARDED로 전이한다. 새 모듈이 자기
 * 핸들러를 등록하면 이 서비스를 수정하지 않아도 회수에 포함된다. 로그인은 ACTIVE만 허용되므로 즉시 차단된다.
 */
@Service
@Transactional(readOnly = true)
public class UserLifecycleService {
  private static final Logger log = LoggerFactory.getLogger(UserLifecycleService.class);

  private final ManagedUserRepository userRepository;
  private final List<OffboardHandler> offboardHandlers;

  public UserLifecycleService(ManagedUserRepository userRepository, List<OffboardHandler> offboardHandlers) {
    this.userRepository = userRepository;
    this.offboardHandlers = offboardHandlers;
  }

  @Transactional
  public OffboardResult offboard(UUID tenantId, UUID userId) {
    ManagedUser user = userRepository.findById(userId)
        .orElseThrow(() -> new ManagedUserNotFoundException(userId));
    if (!tenantId.equals(user.getTenantId())) {
      throw new ManagedUserNotFoundException(userId);
    }
    if (user.hasRole(UserRole.SYSTEM_ADMIN)) {
      throw new IllegalStateException("플랫폼 관리자는 기관 퇴사 처리 대상이 아닙니다.");
    }
    List<OffboardOutcome> outcomes = new ArrayList<>();
    for (OffboardHandler handler : offboardHandlers) {
      try {
        outcomes.add(handler.offboard(tenantId, userId));
      } catch (RuntimeException failure) {
        // 퇴사는 보안 행위라 부분 회수(권한은 지웠는데 자산은 남는 등)를 허용하지 않는다.
        // 예외를 그대로 올려 전체를 롤백하되, 어느 핸들러가 왜 실패했는지 남긴다 —
        // 감싸지 않으면 호출부가 원인을 알 수 없어 복구를 시작할 수조차 없다.
        String handlerName = handler.getClass().getSimpleName();
        log.error("[OFFBOARD] handler {} failed for user {} (tenant {}) — 전체 롤백",
            handlerName, userId, tenantId, failure);
        throw new OffboardFailedException(handlerName, failure);
      }
    }
    user.changeStatus(UserStatus.OFFBOARDED, OffsetDateTime.now());
    userRepository.save(user);
    return new OffboardResult(outcomes);
  }
}
