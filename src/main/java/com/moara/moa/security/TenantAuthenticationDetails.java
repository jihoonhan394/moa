package com.moara.moa.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * 폼 로그인 시 진입 세션에서 읽어온 기관(테넌트) 컨텍스트를 인증에 전달한다.
 * {@code platform=true}면 특정 기관이 아닌 플랫폼(SYSTEM_ADMIN) 로그인이다.
 */
public class TenantAuthenticationDetails extends WebAuthenticationDetails {
  private final UUID tenantId;
  private final boolean platform;
  private final String clientIp;

  public TenantAuthenticationDetails(HttpServletRequest request, UUID tenantId, boolean platform) {
    super(request);
    this.tenantId = tenantId;
    this.platform = platform;
    // 상위의 getRemoteAddress()는 프록시 IP다. 잠금은 실제 출발지 기준이어야 하므로
    // 프록시 헤더까지 본 값을 따로 들고 간다.
    this.clientIp = ClientIpResolver.resolve(request);
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public boolean isPlatform() {
    return platform;
  }

  /** 프록시 헤더까지 반영한 실제 출발지. 로그인 잠금이 이 값으로 센다. */
  public String getClientIp() {
    return clientIp;
  }
}
