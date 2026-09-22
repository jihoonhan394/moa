package com.moara.moa.credential;

import java.util.HexFormat;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * KEK를 설정값 {@code moa.vault.master-key}(64 hex = 32바이트, AES-256)에서 읽는다.
 * 운영에선 환경변수 {@code MOA_VAULT_MASTER_KEY}로 주입. 미설정 시 사용 시점에 명확히 실패한다.
 * 나중에 KMS/Vault 구현으로 교체 가능하도록 {@link KeyProvider}만 노출한다.
 */
@Component
public class EnvKeyProvider implements KeyProvider {
  private static final int CURRENT_VERSION = 1;
  private final SecretKey key;

  public EnvKeyProvider(@Value("${moa.vault.master-key:}") String masterKeyHex) {
    this.key = parse(masterKeyHex);
  }

  private static SecretKey parse(String hex) {
    if (hex == null || hex.isBlank()) {
      return null;
    }
    byte[] raw;
    try {
      raw = HexFormat.of().parseHex(hex.trim());
    } catch (IllegalArgumentException exception) {
      throw new VaultException("볼트 마스터 키 형식 오류(64 hex 문자여야 함).");
    }
    if (raw.length != 32) {
      throw new VaultException("볼트 마스터 키 길이 오류(32바이트/64 hex, AES-256).");
    }
    return new SecretKeySpec(raw, "AES");
  }

  @Override
  public SecretKey masterKey() {
    if (key == null) {
      throw new VaultException("볼트 마스터 키가 설정되지 않았습니다(MOA_VAULT_MASTER_KEY, 64 hex).");
    }
    return key;
  }

  @Override
  public SecretKey masterKey(int version) {
    if (version != CURRENT_VERSION) {
      throw new VaultException("지원하지 않는 키 버전: " + version);
    }
    return masterKey();
  }

  @Override
  public int currentKeyVersion() {
    return CURRENT_VERSION;
  }
}
