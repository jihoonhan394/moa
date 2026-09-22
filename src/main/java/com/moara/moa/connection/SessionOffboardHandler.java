package com.moara.moa.connection;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자의 활성 접속 세션을 CLOSED로 기록(신규 접속은 권한 회수로 차단). */
@Component
public class SessionOffboardHandler implements OffboardHandler {
  private final ConnectionSessionService connectionSessionService;

  public SessionOffboardHandler(ConnectionSessionService connectionSessionService) {
    this.connectionSessionService = connectionSessionService;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("활성 세션", connectionSessionService.closeActiveForUser(tenantId, userId));
  }
}
