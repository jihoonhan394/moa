package com.moara.moa.credential;

import com.moara.moa.user.OffboardHandler;
import com.moara.moa.user.OffboardOutcome;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 퇴사 회수: 사용자에게 공유된 크리덴셜 열람 권한을 모두 회수. */
@Component
public class CredentialShareOffboardHandler implements OffboardHandler {
  private final CredentialShareService shareService;

  public CredentialShareOffboardHandler(CredentialShareService shareService) {
    this.shareService = shareService;
  }

  @Override
  public OffboardOutcome offboard(UUID tenantId, UUID userId) {
    return new OffboardOutcome("크리덴셜 공유", shareService.removeSharesOf(tenantId, userId));
  }
}
