package com.moara.moa.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 플랫폼(SYSTEM_ADMIN) 진입 접근 제어 설정. 값은 환경변수로 주입한다.
 *   MOA_PLATFORM_ENTRY_PATH  플랫폼 로그인 진입 경로(기본 /platform). 비공개 은닉 경로로 덮어쓸 것을 권장.
 *   MOA_PLATFORM_ALLOWED_IPS 허용 IP 목록(비어 있으면 전체 허용). 후속 확장 지점.
 *
 * <p>MVP는 "은닉 경로"만으로 시작하고, IP 허용목록/리버스 프록시 분리/MFA는 이 설정 위에 확장한다.
 */
@Component
@ConfigurationProperties(prefix = "moa.platform")
public class PlatformAccessProperties {
  private String entryPath = "/platform";
  private List<String> allowedIps = List.of();

  public String getEntryPath() {
    return entryPath;
  }

  public void setEntryPath(String entryPath) {
    this.entryPath = (entryPath == null || entryPath.isBlank()) ? "/platform" : entryPath.trim();
  }

  public List<String> getAllowedIps() {
    return allowedIps;
  }

  public void setAllowedIps(List<String> allowedIps) {
    this.allowedIps = allowedIps == null ? List.of() : allowedIps;
  }

  /** 허용 IP가 비어 있으면 전체 허용(MVP). 지정되면 목록에 있어야 통과. */
  public boolean ipAllowed(String remoteIp) {
    return allowedIps.isEmpty() || allowedIps.contains(remoteIp);
  }
}
