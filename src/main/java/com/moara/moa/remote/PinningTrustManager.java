package com.moara.moa.remote;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.net.ssl.X509TrustManager;

/**
 * WinRM(HTTPS)에서 서버 인증서를 <b>고정</b>한다. SSH 호스트 키와 같은 규칙을 TLS에 적용한 것이다.
 *
 * <p>전에는 모든 인증서를 무조건 믿었다. 그 연결로 Basic 인증 헤더가 나가므로, 끼어든 쪽은
 * 아무 자체서명 인증서나 내밀고 계정과 비밀번호를 받아 챙길 수 있었다.
 *
 * <p>공인 CA 검증을 쓰지 않는 이유는 WinRM의 현실이다 — 윈도 호스트는 대개 자체서명 인증서로
 * 5986을 연다. 그것을 거부하면 제어 기능 자체가 못 쓰이고, 무조건 믿으면 지금 상태다. 지문을
 * 기억해 두는 방식이 그 사이를 지난다.
 */
class PinningTrustManager implements X509TrustManager {
  private final RemoteHostKeyStore store;
  private final UUID tenantId;
  private final String host;
  private final int port;

  PinningTrustManager(RemoteHostKeyStore store, UUID tenantId, String host, int port) {
    this.store = store;
    this.tenantId = tenantId;
    this.host = host;
    this.port = port;
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    if (chain == null || chain.length == 0) {
      throw new CertificateException("서버가 인증서를 제시하지 않았습니다");
    }
    X509Certificate leaf = chain[0];
    // 공개키가 아니라 인증서 전체(DER)로 지문을 만든다. 같은 키로 재발급하면 공개키는
    // 그대로라 바뀐 사실이 드러나지 않는다.
    String fingerprint = RemoteHostKeyStore.fingerprintOf(leaf.getEncoded());
    String subject = leaf.getSubjectX500Principal().getName();

    RemoteHostKeyStore.Verdict verdict =
        store.verify(tenantId, host, port, RemoteChannel.TLS, subject, fingerprint);
    if (verdict == RemoteHostKeyStore.Verdict.MISMATCHED) {
      String recorded = store.find(tenantId, host, port, RemoteChannel.TLS)
          .map(RemoteHostKey::getFingerprint).orElse("(없음)");
      throw new CertificateException(
          RemoteHostKeyStore.mismatchMessage(host, port, fingerprint, recorded));
    }
  }

  /** 우리는 클라이언트 인증서를 쓰지 않는다. 서버 쪽에서 요구하면 그때 정한다. */
  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    throw new CertificateException("클라이언트 인증서 검증은 지원하지 않습니다");
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return new X509Certificate[0];
  }
}
