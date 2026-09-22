package com.moara.moa.tracker;

import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 대상 호스트에 TLS 핸드셰이크만 걸어 서버 인증서의 만료일(notAfter)을 읽는다. 발급·설치는 하지 않는다(선넘음 방지).
 * 만료·자기서명 인증서도 만료일을 읽어야 하므로 신뢰 검증은 끈다(읽기 전용 조회 목적).
 */
@Service
public class SslProbeService {
  private static final Logger log = LoggerFactory.getLogger(SslProbeService.class);
  private static final int TIMEOUT_MS = 5000;

  /**
   * TLS 프로브 결과. notAfter=만료일(핵심), notBefore=발급 시작일, subject/issuer=주체·발급자 DN,
   * serial=시리얼(16진), sanNames=주체대체이름(dNSName, ", " 구분). issuer 이후 값은 표시·감사용이며 secret 아님.
   */
  public record ProbeResult(
      LocalDate notAfter, LocalDate notBefore, String subject, String issuer,
      String serial, String sanNames) {}

  /**
   * host:port로 TLS 연결해 서버 인증서 정보(만료일·발급일·발급자·시리얼·SAN)를 읽는다.
   * 실패(연결 불가/TLS 아님 등) 시 빈 값.
   */
  public Optional<ProbeResult> probe(String host, int port) {
    if (host == null || host.isBlank()) {
      return Optional.empty();
    }
    try {
      SSLContext ctx = SSLContext.getInstance("TLS");
      ctx.init(null, new TrustManager[] {TRUST_ALL}, null);
      SSLSocketFactory factory = ctx.getSocketFactory();
      try (SSLSocket socket = (SSLSocket) factory.createSocket()) {
        socket.connect(new java.net.InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        // SNI: 가상호스트가 올바른 인증서를 내려주도록 호스트명을 넘긴다.
        javax.net.ssl.SSLParameters params = socket.getSSLParameters();
        params.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(host)));
        socket.setSSLParameters(params);
        socket.startHandshake();
        java.security.cert.Certificate[] chain = socket.getSession().getPeerCertificates();
        if (chain.length == 0 || !(chain[0] instanceof X509Certificate x509)) {
          return Optional.empty();
        }
        LocalDate notAfter = x509.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate notBefore = x509.getNotBefore().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return Optional.of(new ProbeResult(
            notAfter, notBefore,
            x509.getSubjectX500Principal().getName(), x509.getIssuerX500Principal().getName(),
            x509.getSerialNumber().toString(16), subjectAltNames(x509)));
      }
    } catch (Exception exception) {
      log.info("[SSL] probe failed host={} port={}: {}", host, port, exception.getMessage());
      return Optional.empty();
    }
  }

  /** dNSName(type 2) SAN만 중복 제거·정렬해 ", "로 잇는다. 파싱 실패 시 빈 문자열. */
  private static String subjectAltNames(X509Certificate x509) {
    try {
      java.util.Collection<java.util.List<?>> sans = x509.getSubjectAlternativeNames();
      if (sans == null) {
        return "";
      }
      return sans.stream()
          .filter(e -> e.size() >= 2 && Integer.valueOf(2).equals(e.get(0)) && e.get(1) instanceof String)
          .map(e -> (String) e.get(1))
          .distinct()
          .sorted()
          .collect(java.util.stream.Collectors.joining(", "));
    } catch (java.security.cert.CertificateParsingException exception) {
      return "";
    }
  }

  /** 프로브 결과를 사람이 읽는 한 줄 요약으로. 500자 이내(트래커 detail 컬럼 한계). secret 없음. */
  public static String summarize(ProbeResult result) {
    StringBuilder sb = new StringBuilder();
    sb.append("유효기간 ").append(result.notBefore()).append(" ~ ").append(result.notAfter());
    String issuerCn = commonName(result.issuer());
    if (!issuerCn.isBlank()) {
      sb.append(" · 발급자 ").append(issuerCn);
    }
    if (result.sanNames() != null && !result.sanNames().isBlank()) {
      sb.append(" · SAN ").append(result.sanNames());
    }
    String summary = sb.toString();
    return summary.length() > 500 ? summary.substring(0, 500) : summary;
  }

  /** DN에서 CN 값만 뽑는다(없으면 원문). 예: "CN=*.example.com,O=..." → "*.example.com". */
  private static String commonName(String distinguishedName) {
    if (distinguishedName == null || distinguishedName.isBlank()) {
      return "";
    }
    for (String part : distinguishedName.split(",")) {
      String trimmed = part.trim();
      if (trimmed.regionMatches(true, 0, "CN=", 0, 3)) {
        return trimmed.substring(3).trim();
      }
    }
    return distinguishedName;
  }

  private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
  };
}
