package com.moara.moa.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.moara.moa.tenant.Tenant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CredentialServiceTest {
  private static final UUID TENANT = Tenant.DEFAULT_TENANT_ID;

  @Autowired private CredentialService service;

  @Test
  void createStoresEncryptedAndResolves() {
    Credential credential = service.create(TENANT, form("cred-" + System.nanoTime(), "mtcm", "test-secret-1"));
    assertNotEquals("test-secret-1", credential.getSecretCiphertext()); // 저장은 암호문
    CredentialService.ResolvedCredential resolved = service.resolveSecret(TENANT, credential.getId());
    assertEquals("mtcm", resolved.username());
    assertEquals("test-secret-1", resolved.secret());
  }

  @Test
  void duplicateNameRejected() {
    CredentialForm form = form("dup-" + System.nanoTime(), "u", "s");
    service.create(TENANT, form);
    assertThrows(DuplicateCredentialException.class, () -> service.create(TENANT, form));
  }

  @Test
  void updateSecretChangesResolvedValue() {
    Credential credential = service.create(TENANT, form("cred-" + System.nanoTime(), "u", "old-secret"));
    service.update(TENANT, credential.getId(),
        new CredentialForm(credential.getName(), CredentialType.PASSWORD, "u", "new-secret"));
    assertEquals("new-secret", service.resolveSecret(TENANT, credential.getId()).secret());
  }

  @Test
  void updateWithBlankSecretKeepsOldSecretButUpdatesMeta() {
    Credential credential = service.create(TENANT, form("cred-" + System.nanoTime(), "user1", "keep-me"));
    service.update(TENANT, credential.getId(),
        new CredentialForm(credential.getName(), CredentialType.PASSWORD, "user2", ""));
    CredentialService.ResolvedCredential resolved = service.resolveSecret(TENANT, credential.getId());
    assertEquals("user2", resolved.username()); // 메타 갱신
    assertEquals("keep-me", resolved.secret()); // 비밀 유지
  }

  private CredentialForm form(String name, String username, String secret) {
    return new CredentialForm(name, CredentialType.PASSWORD, username, secret);
  }
}
