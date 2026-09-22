package com.moara.moa.credential;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 봉투암호화 볼트. 비밀마다 랜덤 DEK(AES-256)로 GCM 암호화하고, DEK를 KEK로 다시 GCM 감싼다.
 * 각 블록은 [12B nonce | ciphertext(+16B tag)] 형태이며 base64로 저장된다.
 * 복호화한 평문·DEK는 사용 후 즉시 폐기하며, 예외 메시지에 평문을 담지 않는다.
 *
 * <p>테넌트 바인딩: KEK 래핑 계층의 GCM AAD에 테넌트 식별자를 묶는다. 저장된 (암호문, 래핑DEK) 쌍을
 * 다른 기관 행으로 옮겨도 AAD 불일치로 복호화가 실패한다(테넌트 격리 불변식 강화). AAD 없이 저장된
 * 과거 데이터는 tag 실패 시 AAD 없이 재시도해 하위호환한다(점진 재암호화).
 */
@Component
public class SecretVault {
  private static final int NONCE_LEN = 12;
  private static final int TAG_BITS = 128;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final KeyProvider keyProvider;

  public SecretVault(KeyProvider keyProvider) {
    this.keyProvider = keyProvider;
  }

  /** 테넌트 바인딩 없이 암호화(하위호환·범용). 가능하면 {@link #encrypt(String, UUID)}를 쓴다. */
  public EncryptedSecret encrypt(String plaintext) {
    return encrypt(plaintext, (byte[]) null);
  }

  /** 테넌트에 바인딩해 암호화(KEK 래핑 AAD = 테넌트). 이후 다른 기관에서는 복호화되지 않는다. */
  public EncryptedSecret encrypt(String plaintext, UUID tenantId) {
    return encrypt(plaintext, aad(tenantId));
  }

  private EncryptedSecret encrypt(String plaintext, byte[] aad) {
    byte[] dek = new byte[32];
    RANDOM.nextBytes(dek);
    SecretKey dekKey = new SecretKeySpec(dek, "AES");
    try {
      String secretCiphertext = base64(gcm(Cipher.ENCRYPT_MODE, dekKey, plaintext.getBytes(StandardCharsets.UTF_8), null));
      String dekWrapped = base64(gcm(Cipher.ENCRYPT_MODE, keyProvider.masterKey(), dek, aad));
      return new EncryptedSecret(secretCiphertext, dekWrapped, keyProvider.currentKeyVersion());
    } catch (GeneralSecurityException exception) {
      throw new VaultException("자격증명 암호화 실패", exception);
    } finally {
      Arrays.fill(dek, (byte) 0);
    }
  }

  /** 테넌트 바인딩 없이 복호화(하위호환·범용). */
  public String decrypt(String secretCiphertext, String dekWrapped, int keyVersion) {
    return decrypt(secretCiphertext, dekWrapped, keyVersion, (byte[]) null);
  }

  /** 테넌트에 바인딩해 복호화. 과거(AAD 없이 저장) 데이터는 AAD 없이 자동 재시도한다. */
  public String decrypt(String secretCiphertext, String dekWrapped, int keyVersion, UUID tenantId) {
    return decrypt(secretCiphertext, dekWrapped, keyVersion, aad(tenantId));
  }

  private String decrypt(String secretCiphertext, String dekWrapped, int keyVersion, byte[] aad) {
    byte[] dek = null;
    try {
      dek = unwrapDek(dekWrapped, keyVersion, aad);
      SecretKey dekKey = new SecretKeySpec(dek, "AES");
      byte[] plain = gcm(Cipher.DECRYPT_MODE, dekKey, unbase64(secretCiphertext), null);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException exception) {
      throw new VaultException("자격증명 복호화 실패", exception);
    } finally {
      if (dek != null) {
        Arrays.fill(dek, (byte) 0);
      }
    }
  }

  /** DEK 언래핑. AAD로 시도하고, tag 불일치(과거 AAD 미적용 데이터)면 AAD 없이 한 번 재시도한다. */
  private byte[] unwrapDek(String dekWrapped, int keyVersion, byte[] aad) throws GeneralSecurityException {
    byte[] wrapped = unbase64(dekWrapped);
    try {
      return gcm(Cipher.DECRYPT_MODE, keyProvider.masterKey(keyVersion), wrapped, aad);
    } catch (AEADBadTagException tagMismatch) {
      if (aad == null) {
        throw tagMismatch; // 이미 AAD 없이 시도한 것이므로 실제 손상/키불일치
      }
      return gcm(Cipher.DECRYPT_MODE, keyProvider.masterKey(keyVersion), wrapped, null);
    }
  }

  private static byte[] aad(UUID tenantId) {
    return tenantId == null ? null : ("moa-tenant:" + tenantId).getBytes(StandardCharsets.UTF_8);
  }

  /** ENCRYPT: 랜덤 nonce 생성 후 [nonce|ct] 반환. DECRYPT: 입력에서 nonce 분리 후 복호. aad!=null이면 GCM AAD 적용. */
  private byte[] gcm(int mode, SecretKey key, byte[] input, byte[] aad) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    if (mode == Cipher.ENCRYPT_MODE) {
      byte[] nonce = new byte[NONCE_LEN];
      RANDOM.nextBytes(nonce);
      cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
      if (aad != null) {
        cipher.updateAAD(aad);
      }
      byte[] ciphertext = cipher.doFinal(input);
      byte[] out = new byte[NONCE_LEN + ciphertext.length];
      System.arraycopy(nonce, 0, out, 0, NONCE_LEN);
      System.arraycopy(ciphertext, 0, out, NONCE_LEN, ciphertext.length);
      return out;
    }
    byte[] nonce = Arrays.copyOfRange(input, 0, NONCE_LEN);
    byte[] ciphertext = Arrays.copyOfRange(input, NONCE_LEN, input.length);
    cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
    if (aad != null) {
      cipher.updateAAD(aad);
    }
    return cipher.doFinal(ciphertext);
  }

  private static String base64(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static byte[] unbase64(String value) {
    return Base64.getDecoder().decode(value);
  }
}
