-- 기관별 AI 설정. API 키는 SMTP와 동일한 봉투암호화(SecretVault)로 저장(평문 없음).
-- 제공자: Gemini/Claude/OpenAI/DeepSeek. 폐쇄망은 base_url을 로컬 LLM(OpenAI 호환)으로 지정.
CREATE TABLE ai_settings (
  id                UUID PRIMARY KEY,
  tenant_id         UUID REFERENCES tenants (id),
  provider          VARCHAR(20) NOT NULL,
  model             VARCHAR(100),
  base_url          VARCHAR(255),
  secret_ciphertext VARCHAR(4096),
  dek_wrapped       VARCHAR(4096),
  key_version       INTEGER,
  enabled           BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_ai_settings_provider CHECK (provider IN ('GEMINI', 'CLAUDE', 'OPENAI', 'DEEPSEEK'))
);

CREATE UNIQUE INDEX uq_ai_settings_tenant ON ai_settings (tenant_id);
