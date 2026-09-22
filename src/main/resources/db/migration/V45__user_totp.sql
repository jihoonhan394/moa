-- 2단계 인증(TOTP, Google Authenticator) 토대. 시크릿은 SecretVault 봉투암호화로만 저장(평문 없음).
-- 지금은 등록/검증 메커니즘만 — 민감 작업(접속·재기동) 게이팅 연결은 후속.
CREATE TABLE user_totp (
  user_id           UUID PRIMARY KEY REFERENCES managed_users (id) ON DELETE CASCADE,
  tenant_id         UUID,
  secret_ciphertext VARCHAR(4096),
  dek_wrapped       VARCHAR(4096),
  key_version       INT,
  enabled           BOOLEAN NOT NULL DEFAULT FALSE,
  confirmed_at      TIMESTAMP WITH TIME ZONE,
  created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
