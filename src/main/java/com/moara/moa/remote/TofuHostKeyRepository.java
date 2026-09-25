package com.moara.moa.remote;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.UserInfo;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * JSch에 꽂는 호스트 키 정책. 연결 하나에 하나씩 만들어 쓴다.
 *
 * <p>JSch는 {@code StrictHostKeyChecking=yes}일 때 이 객체의 {@link #check}만 보고 통과 여부를
 * 정한다. 그래서 정책을 여기에 두면 JSch에게 물어볼 것이 남지 않는다 — 콘솔 프롬프트도,
 * known_hosts 파일도 필요 없다.
 *
 * <p>JSch가 넘기는 host 문자열은 포트가 22가 아니면 {@code [호스트]:포트} 꼴로 바뀐다. 그 문자열을
 * 파싱하는 대신 <b>연결을 만들 때 알고 있던 대상</b>을 그대로 들고 있는다 — 파싱할 것이 없으면
 * 틀릴 것도 없다.
 */
class TofuHostKeyRepository implements HostKeyRepository {
  private final RemoteHostKeyStore store;
  private final UUID tenantId;
  private final String host;
  private final int port;

  TofuHostKeyRepository(RemoteHostKeyStore store, UUID tenantId, String host, int port) {
    this.store = store;
    this.tenantId = tenantId;
    this.host = host;
    this.port = port;
  }

  /**
   * 처음 보는 호스트면 기록하고 통과(OK), 기록과 다르면 예외로 막는다.
   *
   * <p>불일치에 {@code CHANGED}를 돌려주면 JSch가 "HostKeyAlreadyExists" 같은 자기 문구로 바꿔
   * 던져, 화면에는 무엇을 해야 하는지 없는 영문 메시지가 뜬다. 여기서 직접 던져야 관리자가
   * 읽고 판단할 수 있는 말이 그대로 올라간다.
   */
  @Override
  public int check(String jschHost, byte[] key) {
    String fingerprint = RemoteHostKeyStore.fingerprintOf(key);
    RemoteHostKeyStore.Verdict verdict = store.verify(
        tenantId, host, port, RemoteChannel.SSH, keyTypeOf(key), fingerprint);

    if (verdict == RemoteHostKeyStore.Verdict.MISMATCHED) {
      String recorded = store.find(tenantId, host, port, RemoteChannel.SSH)
          .map(RemoteHostKey::getFingerprint).orElse("(없음)");
      throw new HostIdentityMismatchException(
          RemoteHostKeyStore.mismatchMessage(host, port, fingerprint, recorded));
    }
    return OK;
  }

  /**
   * SSH 키 블롭의 맨 앞에는 길이(4바이트)와 알고리즘 이름이 온다(RFC 4253 §6.6).
   * 사람이 목록에서 "ssh-ed25519"를 보고 알아볼 수 있게 꺼내 둔다.
   */
  private static String keyTypeOf(byte[] key) {
    if (key == null || key.length < 4) {
      return "unknown";
    }
    int length = ((key[0] & 0xff) << 24) | ((key[1] & 0xff) << 16)
        | ((key[2] & 0xff) << 8) | (key[3] & 0xff);
    if (length <= 0 || length > 64 || 4 + length > key.length) {
      return "unknown";
    }
    return new String(key, 4, length, StandardCharsets.US_ASCII);
  }

  // ── 아래는 JSch 인터페이스를 채우기 위한 것. 우리 저장소는 check()로만 판단한다. ──

  /** JSch가 부르지 않는다(check가 이미 OK를 돌려주므로). 기록은 check 안에서 한다. */
  @Override
  public void add(HostKey hostkey, UserInfo ui) {
    // 의도적으로 비움 — 기록 시점을 한 곳(check)에 두어야 정책이 갈라지지 않는다.
  }

  @Override
  public void remove(String host, String type) {
    store.forget(tenantId, this.host, this.port);
  }

  @Override
  public void remove(String host, String type, byte[] key) {
    store.forget(tenantId, this.host, this.port);
  }

  @Override
  public String getKnownHostsRepositoryID() {
    return "moa:remote_host_keys";
  }

  @Override
  public HostKey[] getHostKey() {
    return new HostKey[0];
  }

  @Override
  public HostKey[] getHostKey(String host, String type) {
    return new HostKey[0];
  }
}
