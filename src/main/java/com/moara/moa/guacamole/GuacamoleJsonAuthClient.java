package com.moara.moa.guacamole;

import com.moara.moa.connection.SessionProtocol;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * guacamole-auth-json 방식으로 1회성 인증 토큰을 만든다. 대상 서버 자격증명을 담은 연결 정의를
 * HMAC-SHA256 서명 + AES-128-CBC(zero IV) 암호화하여 Guacamole {@code /api/tokens}에 전달하고,
 * 받은 authToken으로 클라이언트 접속 URL을 만든다. 관리자 계정/DB 없이 동작하며, 공유키는 env로만 주입된다.
 * JSON 라이브러리 결합을 피하려고 페이로드는 수동 생성(이스케이프)하고 응답은 정규식으로 파싱한다.
 */
@Component
public class GuacamoleJsonAuthClient implements GuacamoleClient {
  private static final byte[] ZERO_IV = new byte[16];
  private static final Pattern AUTH_TOKEN = Pattern.compile("\"authToken\"\\s*:\\s*\"([^\"]+)\"");

  private final GuacamoleProperties properties;
  private final HttpClient httpClient;

  public GuacamoleJsonAuthClient(GuacamoleProperties properties) {
    this.properties = properties;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  @Override
  public boolean isEnabled() {
    return properties.isEnabled();
  }

  @Override
  public GuacamoleLaunch createSession(GuacamoleConnectionRequest request) {
    if (!properties.isEnabled()) {
      throw new GuacamoleException("Guacamole 연동이 설정되지 않았습니다(MOA_GUACAMOLE_BASE_URL/SECRET_KEY).");
    }
    String data = encryptData(buildConnectionJson(request));
    String authToken = requestToken(data);
    String clientId = base64(request.displayName() + "\0c\0json");
    String redirectUrl = properties.getBaseUrl() + "/#/client/" + clientId + "?token=" + authToken;
    return new GuacamoleLaunch(redirectUrl);
  }

  /** 연결 정의 JSON을 만든다. 자격증명은 이 토큰에만 실리고 저장되지 않는다. */
  String buildConnectionJson(GuacamoleConnectionRequest request) {
    boolean rdp = request.protocol() == SessionProtocol.RDP;
    long expires = System.currentTimeMillis() + properties.getConnectionTtlSeconds() * 1000L;

    StringBuilder parameters = new StringBuilder();
    parameters.append('{')
        .append(field("hostname", request.host())).append(',')
        .append(field("port", Integer.toString(request.port()))).append(',')
        .append(field("username", request.username())).append(',')
        .append(field("password", request.password()));
    if (rdp) {
      parameters.append(',').append(field("security", "any"))
          .append(',').append(field("ignore-cert", Boolean.toString(properties.isIgnoreCert())))
          .append(',').append(field("resize-method", "display-update"));
    }
    parameters.append('}');

    return "{" + field("username", "moa") + ","
        + "\"expires\":" + expires + ","
        + "\"connections\":{"
        + jsonString(request.displayName()) + ":{"
        + field("protocol", rdp ? "rdp" : "ssh") + ","
        + "\"parameters\":" + parameters
        + "}}}";
  }

  /** HMAC-SHA256 서명(앞에 붙임) + AES-128-CBC(zero IV) 암호화 → base64. */
  String encryptData(String json) {
    try {
      byte[] key = HexFormat.of().parseHex(properties.getSecretKey());
      byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      byte[] signature = mac.doFinal(jsonBytes);

      byte[] signed = new byte[signature.length + jsonBytes.length];
      System.arraycopy(signature, 0, signed, 0, signature.length);
      System.arraycopy(jsonBytes, 0, signed, signature.length, jsonBytes.length);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(ZERO_IV));
      byte[] encrypted = cipher.doFinal(signed);
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (IllegalArgumentException exception) {
      throw new GuacamoleException("SECRET_KEY 형식 오류(32 hex 문자여야 함).", exception);
    } catch (Exception exception) {
      throw new GuacamoleException("토큰 암호화 실패", exception);
    }
  }

  private String requestToken(String data) {
    try {
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(properties.getBaseUrl() + "/api/tokens"))
          .timeout(Duration.ofSeconds(15))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(
              "data=" + URLEncoder.encode(data, StandardCharsets.UTF_8)))
          .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() / 100 != 2) {
        throw new GuacamoleException("Guacamole 토큰 발급 실패(status=" + response.statusCode() + ")");
      }
      Matcher matcher = AUTH_TOKEN.matcher(response.body());
      if (!matcher.find()) {
        throw new GuacamoleException("Guacamole 응답에 authToken이 없습니다.");
      }
      return matcher.group(1);
    } catch (GuacamoleException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new GuacamoleException("Guacamole 토큰 요청 실패: " + exception.getMessage(), exception);
    }
  }

  private String field(String name, String value) {
    return jsonString(name) + ":" + jsonString(value);
  }

  private String jsonString(String value) {
    StringBuilder builder = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> builder.append("\\\"");
        case '\\' -> builder.append("\\\\");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> {
          if (c < 0x20) {
            builder.append(String.format("\\u%04x", (int) c));
          } else {
            builder.append(c);
          }
        }
      }
    }
    return builder.append('"').toString();
  }

  private String base64(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
