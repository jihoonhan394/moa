-- 입사 온보딩: 부서/직무 템플릿 → 신입에게 자산·솔루션·위키 일괄 배정 + 체크리스트/필독동의.
-- (퇴사 회수의 거울. 하나의 모듈로 응집.)

CREATE TABLE onboarding_templates (
  id         UUID PRIMARY KEY,
  tenant_id  UUID NOT NULL REFERENCES tenants (id),
  name       VARCHAR(120) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_onb_templates_tenant ON onboarding_templates (tenant_id, name);

-- 템플릿 구성 항목: 즉시 실행(솔루션 배정·위키 열람 부여) 또는 체크리스트로 복사(할일·필독동의).
CREATE TABLE onboarding_template_items (
  id          UUID PRIMARY KEY,
  template_id UUID NOT NULL REFERENCES onboarding_templates (id) ON DELETE CASCADE,
  item_type   VARCHAR(20) NOT NULL,   -- ASSIGN_SOLUTION / GRANT_WIKI_SPACE / TASK / ACK_DOC
  ref_id      UUID,                   -- 솔루션/공간 식별자(ASSIGN/GRANT/ACK_DOC), 없으면 NULL
  label       VARCHAR(300),           -- 할일/필독문서 표시 문구
  sort_order  INT NOT NULL DEFAULT 0,
  CONSTRAINT ck_onb_item_type CHECK (item_type IN ('ASSIGN_SOLUTION', 'GRANT_WIKI_SPACE', 'TASK', 'ACK_DOC'))
);
CREATE INDEX idx_onb_items_template ON onboarding_template_items (template_id, sort_order);

-- 신입 개인 체크리스트(템플릿 적용 시 TASK/ACK_DOC 항목이 복사된다).
CREATE TABLE onboarding_tasks (
  id         UUID PRIMARY KEY,
  tenant_id  UUID NOT NULL REFERENCES tenants (id),
  user_id    UUID NOT NULL REFERENCES managed_users (id) ON DELETE CASCADE,
  kind       VARCHAR(20) NOT NULL,   -- TASK / ACK_DOC
  label      VARCHAR(300) NOT NULL,
  ref_id     UUID,                   -- ACK_DOC → 위키 공간/문서
  done       BOOLEAN NOT NULL DEFAULT FALSE,
  done_at    TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_onb_task_kind CHECK (kind IN ('TASK', 'ACK_DOC'))
);
CREATE INDEX idx_onb_tasks_user ON onboarding_tasks (tenant_id, user_id, done);
