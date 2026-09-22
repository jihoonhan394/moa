-- 초대 기반 온보딩. 아이디=이메일 지원을 위해 username 폭 확대(이메일 ≤255).
ALTER TABLE managed_users ALTER COLUMN username TYPE VARCHAR(255);

-- 초대: 관리자/부서장이 이메일로 초대(부서·역할·온보딩 템플릿 미리 지정). 토큰은 해시로만 저장.
CREATE TABLE user_invitations (
  id                     UUID PRIMARY KEY,
  tenant_id              UUID NOT NULL REFERENCES tenants (id),
  email                  VARCHAR(255) NOT NULL,
  name                   VARCHAR(100),
  group_id               UUID,
  roles                  VARCHAR(200) NOT NULL DEFAULT 'USER',  -- 콤마구분 역할(USER / USER,ASSET_MANAGER 등)
  onboarding_template_id UUID,
  token_hash             VARCHAR(100) NOT NULL,                 -- SHA-256(hex) — 원문 토큰은 저장 안 함
  status                 VARCHAR(20) NOT NULL,                  -- PENDING / ACCEPTED / REVOKED
  invited_by_user_id     UUID,
  created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at             TIMESTAMP WITH TIME ZONE NOT NULL,
  accepted_at            TIMESTAMP WITH TIME ZONE,
  CONSTRAINT ck_invite_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED'))
);
CREATE INDEX idx_invitations_tenant ON user_invitations (tenant_id, status);
CREATE UNIQUE INDEX uq_invitations_token ON user_invitations (token_hash);
