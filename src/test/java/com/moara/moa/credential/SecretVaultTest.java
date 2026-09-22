package com.moara.moa.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecretVaultTest {
  private static final String KEK = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
  private final SecretVault vault = new SecretVault(new EnvKeyProvider(KEK));

  @Test
  void encryptsAndDecryptsRoundTrip() {
    EncryptedSecret enc = vault.encrypt("test-secret-1-secret");
    assertNotEquals("test-secret-1-secret", enc.secretCiphertext());
    assertFalse(enc.secretCiphertext().contains("ssw0rd")); // 평문 흔적 없음
    assertEquals("test-secret-1-secret",
        vault.decrypt(enc.secretCiphertext(), enc.dekWrapped(), enc.keyVersion()));
  }

  @Test
  void sameInputYieldsDifferentCiphertext() {
    EncryptedSecret a = vault.encrypt("same-value");
    EncryptedSecret b = vault.encrypt("same-value");
    assertNotEquals(a.secretCiphertext(), b.secretCiphertext()); // 랜덤 nonce/DEK
    assertEquals("same-value", vault.decrypt(a.secretCiphertext(), a.dekWrapped(), a.keyVersion()));
    assertEquals("same-value", vault.decrypt(b.secretCiphertext(), b.dekWrapped(), b.keyVersion()));
  }

  @Test
  void tamperedCiphertextFailsAuthentication() {
    EncryptedSecret enc = vault.encrypt("secret");
    byte[] raw = Base64.getDecoder().decode(enc.secretCiphertext());
    raw[raw.length - 1] ^= 0x01; // GCM 태그 훼손
    String tampered = Base64.getEncoder().encodeToString(raw);
    assertThrows(VaultException.class, () -> vault.decrypt(tampered, enc.dekWrapped(), enc.keyVersion()));
  }

  @Test
  void missingMasterKeyFails() {
    SecretVault noKey = new SecretVault(new EnvKeyProvider(""));
    assertThrows(VaultException.class, () -> noKey.encrypt("x"));
  }

  @Test
  void tenantBoundSecretDecryptsForSameTenant() {
    UUID tenant = UUID.randomUUID();
    EncryptedSecret enc = vault.encrypt("bound-secret", tenant);
    assertEquals("bound-secret",
        vault.decrypt(enc.secretCiphertext(), enc.dekWrapped(), enc.keyVersion(), tenant));
  }

  @Test
  void tenantBoundSecretRejectedForOtherTenant() {
    UUID owner = UUID.randomUUID();
    UUID attacker = UUID.randomUUID();
    EncryptedSecret enc = vault.encrypt("bound-secret", owner);
    // 다른 기관 id로는 복호화 실패(테넌트 격리 강화). 3-arg(테넌트 미지정)도 실패.
    assertThrows(VaultException.class,
        () -> vault.decrypt(enc.secretCiphertext(), enc.dekWrapped(), enc.keyVersion(), attacker));
    assertThrows(VaultException.class,
        () -> vault.decrypt(enc.secretCiphertext(), enc.dekWrapped(), enc.keyVersion()));
  }

  @Test
  void legacyUnboundSecretStillDecryptsWithTenantAad() {
    // AAD 없이 저장된 과거 데이터는 테넌트 지정 복호화에서도 폴백으로 읽힌다(점진 마이그레이션).
    EncryptedSecret legacy = vault.encrypt("legacy-secret");
    assertEquals("legacy-secret",
        vault.decrypt(legacy.secretCiphertext(), legacy.dekWrapped(), legacy.keyVersion(), UUID.randomUUID()));
  }
}
