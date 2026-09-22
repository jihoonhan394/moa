package com.moara.moa.guacamole;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Guacamole 연동 설정. 값은 환경변수로만 주입한다(비밀정보 DB/코드 저장 금지).
 *   MOA_GUACAMOLE_BASE_URL   예: http://localhost:8080/guacamole
 *   MOA_GUACAMOLE_SECRET_KEY 128-bit 공유키(32 hex). guacamole-auth-json의 JSON_SECRET_KEY와 동일해야 함.
 * 두 값이 모두 있어야 연동이 활성화된다(없으면 접속 시 비활성 안내).
 */
@Component
@ConfigurationProperties(prefix = "moa.guacamole")
public class GuacamoleProperties {
  private String baseUrl = "";
  private String secretKey = "";
  private long connectionTtlSeconds = 300;
  private boolean ignoreCert = true;

  public boolean isEnabled() {
    return !baseUrl.isBlank() && !secretKey.isBlank();
  }

  public String getBaseUrl() { return baseUrl; }
  public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl.trim(); }

  public String getSecretKey() { return secretKey; }
  public void setSecretKey(String secretKey) { this.secretKey = secretKey == null ? "" : secretKey.trim(); }

  public long getConnectionTtlSeconds() { return connectionTtlSeconds; }
  public void setConnectionTtlSeconds(long connectionTtlSeconds) { this.connectionTtlSeconds = connectionTtlSeconds; }

  public boolean isIgnoreCert() { return ignoreCert; }
  public void setIgnoreCert(boolean ignoreCert) { this.ignoreCert = ignoreCert; }
}
