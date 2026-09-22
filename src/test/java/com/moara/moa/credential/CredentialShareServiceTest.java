package com.moara.moa.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moara.moa.tenant.CreateTenantCommand;
import com.moara.moa.tenant.Tenant;
import com.moara.moa.tenant.TenantService;
import com.moara.moa.user.ManagedUser;
import com.moara.moa.user.ManagedUserService;
import com.moara.moa.user.UserForm;
import com.moara.moa.user.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 크리덴셜 공유: 공유받은 사용자만 열람 가능, 아닌 사용자는 거부, 퇴사 시 회수. */
@SpringBootTest
@ActiveProfiles("test")
class CredentialShareServiceTest {
  @Autowired private CredentialShareService shareService;
  @Autowired private CredentialService credentialService;
  @Autowired private TenantService tenantService;
  @Autowired private ManagedUserService userService;

  @Test
  void onlySharedUserCanReveal() {
    Tenant tenant = tenantService.createTenant(new CreateTenantCommand("공유사", "CS" + System.nanoTime()));
    UUID t = tenant.getId();
    ManagedUser shared = newUser(t);
    ManagedUser other = newUser(t);
    Credential cred = credentialService.create(t,
        new CredentialForm("공유기", CredentialType.PASSWORD, "admin", "router-pw-123"));

    shareService.share(t, cred.getId(), shared.getId());

    // 공유받은 사용자: 열람 O, 목록에 보임.
    assertThat(shareService.canReveal(t, cred.getId(), shared.getId())).isTrue();
    assertThat(shareService.reveal(t, cred.getId(), shared.getId()).secret()).isEqualTo("router-pw-123");
    assertThat(shareService.sharedCredentials(t, shared.getId()))
        .extracting(Credential::getId).contains(cred.getId());

    // 공유 안 받은 사용자: 열람 거부, 목록 비어 있음.
    assertThat(shareService.canReveal(t, cred.getId(), other.getId())).isFalse();
    assertThatThrownBy(() -> shareService.reveal(t, cred.getId(), other.getId()))
        .isInstanceOf(CredentialNotFoundException.class);
    assertThat(shareService.sharedCredentials(t, other.getId())).isEmpty();

    // 퇴사 회수.
    assertThat(shareService.removeSharesOf(t, shared.getId())).isEqualTo(1);
    assertThat(shareService.canReveal(t, cred.getId(), shared.getId())).isFalse();
  }

  private ManagedUser newUser(UUID tenantId) {
    String username = "u" + System.nanoTime();
    return userService.create(tenantId,
        new UserForm(username, "사원", username + "@example.com", "safe-password-123", UserStatus.ACTIVE));
  }
}
