package com.moara.moa.user;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
      outcomes.add(handler.offboard(tenantId, userId));
    }
    user.changeStatus(UserStatus.OFFBOARDED, OffsetDateTime.now());
    userRepository.save(user);
    return new OffboardResult(outcomes);
  }
}
