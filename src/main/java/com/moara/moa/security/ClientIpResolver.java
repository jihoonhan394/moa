package com.moara.moa.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 리버스 프록시 뒤에서 실제 클라이언트 IP를 최대한 복원한다.
 * 우선순위: X-Forwarded-For 체인의 <b>첫 공인 IP</b>(프록시·사설 대역 건너뜀) → 첫 항목 →
 * X-Real-IP → 원격주소(직접 연결/프록시 IP).
 * <p>프록시가 아무 헤더도 넘기지 않으면(예: nginx가 X-Real-IP/X-Forwarded-For 미설정) 원격주소만
 * 보이므로 게이트웨이(192.168.x)가 찍힌다 — 그 경우는 프록시 설정을 고쳐야 한다(앱 단독으론 불가).
 */
public final class ClientIpResolver {
  private ClientIpResolver() {}

  public static String resolve(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      String first = null;
      for (String part : xff.split(",")) {
        String ip = part.trim();
        if (ip.isEmpty()) {
          continue;
        }
        if (first == null) {
          first = ip;
        }
        if (!isPrivate(ip)) {
          return ip; // 체인 중 첫 공인 IP = 원 클라이언트일 가능성이 가장 높다.
        }
      }
      if (first != null) {
        return first; // 전부 사설이면 최소한 맨 앞(원 클라이언트에 가장 가까움).
      }
    }
    String real = request.getHeader("X-Real-IP");
    if (real != null && !real.isBlank()) {
      return real.trim();
    }
    return request.getRemoteAddr();
  }

  /** 사설/루프백 대역인지(프록시 홉으로 간주). */
  static boolean isPrivate(String ip) {
    if (ip == null) {
      return true;
    }
    return ip.startsWith("10.")
        || ip.startsWith("192.168.")
        || ip.startsWith("127.")
        || ip.equals("::1")
        || ip.startsWith("fc") || ip.startsWith("fd") // IPv6 ULA
        || ip.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
  }
}
