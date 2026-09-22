-- 기관/플랫폼 SMTP 설정. tenant_id=NULL 이면 플랫폼(MOA) 스코프(기관 관리자에게 발송),
-- tenant_id 지정이면 해당 기관 스코프(기관이 자기 사용자에게 발송). 스코프별 단일 행은 서비스가 보장.
-- SMTP 비밀번호는 평문 저장 금지 — 봉투암호화(secret_ciphertext/dek_wrapped/key_version)로만 보관.
CREATE TABLE mail_settings (
  id                UUID PRIMARY KEY,
  tenant_id         UUID REFERENCES tenants (id),
  host              VARCHAR(255) NOT NULL,
  port              INTEGER NOT NULL,
  username          VARCHAR(255),
  secret_ciphertext VARCHAR(4096),
  dek_wrapped       VARCHAR(4096),
  key_version       INTEGER,
  from_address      VARCHAR(255) NOT NULL,
  from_name         VARCHAR(100),
  starttls          BOOLEAN NOT NULL DEFAULT TRUE,
  enabled           BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
-- 기관 스코프는 기관당 단일 행만 허용(경합 시 중복 삽입을 DB가 거부). 플랫폼(tenant_id NULL) 스코프는
-- SQL이 NULL을 서로 다르게 취급하므로 이 유니크로는 단일화되지 않는다 → 애플리케이션이 고정 PK로 단일화한다.
CREATE UNIQUE INDEX uq_mail_settings_tenant ON mail_settings (tenant_id);
