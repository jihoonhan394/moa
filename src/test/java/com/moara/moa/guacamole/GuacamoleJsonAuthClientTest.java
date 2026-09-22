package com.moara.moa.guacamole;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class GuacamoleJsonAuthClientTest {
  private static final String SECRET = "00112233445566778899aabbccddeeff"; // 128-bit, 32 hex

  @Test
  void encryptDataIsHmacSignedAndDecryptable() throws Exception {
    GuacamoleProperties props = new GuacamoleProperties();
    props.setSecretKey(SECRET);
    props.setBaseUrl("http://guac");
    GuacamoleJsonAuthClient client = new GuacamoleJsonAuthClient(props);

    String json = "{\"username\":\"moa\",\"connections\":{}}";
    String data = client.encryptData(json);

    // guacamole-auth-json 포맷: AES-128-CBC(zero IV)로 복호화하면 [HMAC-SHA256(32B)] + [json].
    byte[] key = HexFormat.of().parseHex(SECRET);
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(new byte[16]));
    byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(data));

    byte[] signature = Arrays.copyOfRange(decrypted, 0, 32);
    String recovered = new String(decrypted, 32, decrypted.length - 32, StandardCharsets.UTF_8);
    assertEquals(json, recovered);

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    assertArrayEquals(mac.doFinal(recovered.getBytes(StandardCharsets.UTF_8)), signature);
  }

  @Test
  void isDisabledWithoutConfig() {
    assertFalse(new GuacamoleJsonAuthClient(new GuacamoleProperties()).isEnabled());
  }
}
