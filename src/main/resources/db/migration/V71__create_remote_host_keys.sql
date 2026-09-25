-- 원격 호스트 신원 기록 (TOFU — trust on first use).
--
-- 지금까지 제어 채널은 상대를 확인하지 않고 붙었다. SSH는 StrictHostKeyChecking=no,
-- WinRM은 모든 인증서를 믿는 TrustManager였다. 둘 다 "누구든 그 주소에 답하면 그가
-- 맞다"는 뜻이고, 그 연결로 넘어가는 것은 볼트에서 꺼낸 평문 자격증명이다. 중간에
-- 끼어든 쪽은 비밀번호를 받아 챙기고 진짜 서버로 넘겨 주기만 하면 된다.
--
-- 왜 지금까지 못 고쳤나: 검증을 켜는 순간 등록된 키가 하나도 없어 모든 제어가 막힌다.
-- TOFU는 그 사이를 지난다 — 처음 본 호스트는 신원을 기록하고 통과시키고, 그 다음부터
-- 바뀌면 막는다. 완벽한 보증은 아니지만(첫 연결이 이미 가로채였다면 그것을 기록한다),
-- 두 번째 연결부터는 조용한 중간자를 불가능하게 만든다.
--
-- 기관별로 나누는 이유: 사설망 주소는 기관마다 다른 기계다. A기관의 192.168.0.10과
-- B기관의 192.168.0.10은 남남이고, 한 표에 섞으면 서로의 키를 불일치로 판정한다.
CREATE TABLE remote_host_keys (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    host          VARCHAR(255) NOT NULL,
    port          INT          NOT NULL,
    -- SSH | TLS. 같은 호스트라도 채널마다 신원이 다르다(호스트 키 ↔ 인증서).
    channel       VARCHAR(16)  NOT NULL,
    -- SSH는 키 알고리즘(ssh-ed25519 등), TLS는 인증서 주체(CN).
    key_type      VARCHAR(255) NOT NULL,
    -- SHA-256 지문. 키 원문을 두지 않는 이유는 비교에 필요 없기 때문이다.
    fingerprint   VARCHAR(128) NOT NULL,
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 한 기관의 한 호스트:포트에는 채널당 신원이 하나뿐이다. 둘이 되면 어느 쪽과
-- 비교해야 하는지 알 수 없어 검증이 무의미해진다.
CREATE UNIQUE INDEX uq_remote_host_keys_target
    ON remote_host_keys (tenant_id, host, port, channel);
