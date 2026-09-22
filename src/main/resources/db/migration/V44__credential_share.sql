-- 크리덴셜 공유: 특정 자격증명을 특정 사용자에게 '열람' 권한으로 공유(경영지원 등 비IT 사용자가
-- 공유기/프린터 id·pw를 확인). 공유는 INFRA 관리자가 부여. 열람은 감사 기록된다.
CREATE TABLE credential_shares (
  id            UUID PRIMARY KEY,
  tenant_id     UUID NOT NULL REFERENCES tenants (id),
  credential_id UUID NOT NULL REFERENCES credentials (id) ON DELETE CASCADE,
  user_id       UUID NOT NULL REFERENCES managed_users (id) ON DELETE CASCADE,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_cred_share UNIQUE (credential_id, user_id)
);
CREATE INDEX idx_cred_shares_user ON credential_shares (tenant_id, user_id);

-- 설정법 등 위키 연동(공유기 설정, SMTP 설정법 문서).
ALTER TABLE credentials ADD COLUMN wiki_space_id UUID;
