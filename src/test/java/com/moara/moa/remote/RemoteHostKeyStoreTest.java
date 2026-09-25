package com.moara.moa.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * TOFU(trust on first use)의 판단. 제어 채널이 <b>누구에게</b> 자격증명을 건네는지를 정하는
 * 규칙이라, 여기가 틀리면 볼트도 감사도 의미가 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RemoteHostKeyStoreTest {
  @Autowired private RemoteHostKeyStore store;

  /** 처음 본 호스트는 기록하고 통과. 같은 지문으로 다시 오면 통과. */
  @Test
  void firstSightIsRecordedThenMatched() {
    UUID tenant = UUID.randomUUID();
    String host = "host-" + System.nanoTime();

    assertEquals(RemoteHostKeyStore.Verdict.RECORDED,
        store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:aaa"));
    assertEquals(RemoteHostKeyStore.Verdict.MATCHED,
        store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:aaa"));

    assertTrue(store.find(tenant, host, 22, RemoteChannel.SSH).isPresent());
  }

  /** 지문이 달라지면 막는다. 서버 재설치인지 중간자인지 코드는 구별할 수 없다. */
  @Test
  void changedFingerprintIsMismatch() {
    UUID tenant = UUID.randomUUID();
    String host = "host-" + System.nanoTime();
    store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:original");

    assertEquals(RemoteHostKeyStore.Verdict.MISMATCHED,
        store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:impostor"));

    // 막았다고 해서 기록을 덮어쓰지 않는다 — 덮어쓰면 공격자가 한 번 붙는 것만으로
    // 자기 키를 정식 등록시킬 수 있다.
    assertEquals("SHA256:original",
        store.find(tenant, host, 22, RemoteChannel.SSH).orElseThrow().getFingerprint());
  }

  /** 지운 뒤에는 다시 "처음"이 된다 — 재설치에서 빠져나올 문. */
  @Test
  void forgettingAllowsReRecording() {
    UUID tenant = UUID.randomUUID();
    String host = "host-" + System.nanoTime();
    store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:old");

    store.forget(tenant, host, 22);

    assertEquals(RemoteHostKeyStore.Verdict.RECORDED,
        store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:new"));
  }

  /**
   * <b>기관마다 따로 본다.</b> 사설망 주소는 기관마다 다른 기계라, 한 표에 섞으면 A기관의
   * 192.168.0.10 때문에 B기관의 제어가 "신원 불일치"로 막힌다.
   */
  @Test
  void tenantsDoNotShareHostIdentity() {
    String host = "192.168.0.10";
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();

    store.verify(tenantA, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:machine-a");

    assertEquals(RemoteHostKeyStore.Verdict.RECORDED,
        store.verify(tenantB, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:machine-b"),
        "다른 기관의 같은 사설 주소를 같은 기계로 봤다");
  }

  /** 채널이 다르면 신원도 다르다 — 같은 호스트의 SSH 키와 TLS 인증서는 별개다. */
  @Test
  void channelsAreTrackedSeparately() {
    UUID tenant = UUID.randomUUID();
    String host = "host-" + System.nanoTime();

    store.verify(tenant, host, 5986, RemoteChannel.SSH, "ssh-ed25519", "SHA256:ssh-key");

    assertEquals(RemoteHostKeyStore.Verdict.RECORDED,
        store.verify(tenant, host, 5986, RemoteChannel.TLS, "CN=win-01", "SHA256:tls-cert"));
  }

  /** 포트가 다르면 다른 대상이다(같은 장비의 다른 서비스일 수 있다). */
  @Test
  void portIsPartOfIdentity() {
    UUID tenant = UUID.randomUUID();
    String host = "host-" + System.nanoTime();

    store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:a");

    assertEquals(RemoteHostKeyStore.Verdict.RECORDED,
        store.verify(tenant, host, 2222, RemoteChannel.SSH, "ssh-ed25519", "SHA256:b"));
  }

  /**
   * 기록은 <b>읽기 전용 트랜잭션 안에서도</b> 남아야 한다. 제어 화면이
   * {@code @Transactional(readOnly = true)}라, 같은 트랜잭션에 얹히면 Hibernate가 flush하지
   * 않아 기록이 조용히 사라지고 매번 "처음 본 호스트"가 된다 — 검증이 아무 일도 하지 않게 된다.
   */
  @Test
  @Transactional(readOnly = true)
  void recordSurvivesInsideReadOnlyTransaction() {
    UUID tenant = UUID.randomUUID();
    String host = "host-" + System.nanoTime();

    store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:inside-readonly");

    assertEquals(RemoteHostKeyStore.Verdict.MISMATCHED,
        store.verify(tenant, host, 22, RemoteChannel.SSH, "ssh-ed25519", "SHA256:someone-else"),
        "읽기 전용 트랜잭션 안에서 기록이 사라졌다 — 검증이 무력화된다");
  }

  /** 지문 표기는 OpenSSH와 같게 둔다 — 사람이 서버에서 본 값과 눈으로 대조한다. */
  @Test
  void fingerprintLooksLikeOpenSsh() {
    String fingerprint = RemoteHostKeyStore.fingerprintOf("key-bytes".getBytes(StandardCharsets.UTF_8));

    assertTrue(fingerprint.startsWith("SHA256:"), fingerprint);
    assertEquals(7 + 43, fingerprint.length(), "SHA-256 base64(패딩 없음)는 43자");
    assertNotEquals(fingerprint,
        RemoteHostKeyStore.fingerprintOf("other-bytes".getBytes(StandardCharsets.UTF_8)));
  }

  /** 불일치는 무엇을 해야 하는지까지 말해야 한다 — 관리자가 읽고 판단할 문장이다. */
  @Test
  void mismatchMessageTellsWhatToDo() {
    String message = RemoteHostKeyStore.mismatchMessage("10.0.0.5", 22, "SHA256:new", "SHA256:old");

    assertTrue(message.contains("10.0.0.5:22"), message);
    assertTrue(message.contains("SHA256:new") && message.contains("SHA256:old"));
    assertTrue(message.contains("다시 설치"), "재설치 시 복구 방법이 없다");
    assertTrue(message.contains("중간자"), "의심해야 할 상황을 알리지 않는다");
  }

  /** JSch 어댑터도 같은 규칙을 따른다 — 불일치면 연결 자체를 세운다. */
  @Test
  void jschAdapterBlocksOnMismatch() {
    UUID tenant = UUID.randomUUID();
    String host = "host-" + System.nanoTime();
    byte[] first = sshKeyBlob("ssh-ed25519", "first");
    byte[] impostor = sshKeyBlob("ssh-ed25519", "impostor");
    TofuHostKeyRepository repository = new TofuHostKeyRepository(store, tenant, host, 22);

    assertEquals(TofuHostKeyRepository.OK, repository.check(host, first));
    assertEquals(TofuHostKeyRepository.OK, repository.check(host, first));

    HostIdentityMismatchException thrown = assertThrows(HostIdentityMismatchException.class,
        () -> repository.check(host, impostor));
    assertTrue(thrown.getMessage().contains(host + ":22"), thrown.getMessage());

    // 키 종류를 블롭에서 읽어 목록에 보여 준다.
    assertEquals("ssh-ed25519", store.find(tenant, host, 22, RemoteChannel.SSH)
        .orElseThrow().getKeyType());
  }

  /** SSH 키 블롭: 길이(4바이트) + 알고리즘 이름 + 나머지(RFC 4253 §6.6). */
  private static byte[] sshKeyBlob(String algorithm, String body) {
    byte[] name = algorithm.getBytes(StandardCharsets.US_ASCII);
    byte[] rest = body.getBytes(StandardCharsets.US_ASCII);
    byte[] blob = new byte[4 + name.length + rest.length];
    blob[0] = (byte) (name.length >>> 24);
    blob[1] = (byte) (name.length >>> 16);
    blob[2] = (byte) (name.length >>> 8);
    blob[3] = (byte) name.length;
    System.arraycopy(name, 0, blob, 4, name.length);
    System.arraycopy(rest, 0, blob, 4 + name.length, rest.length);
    return blob;
  }
}
