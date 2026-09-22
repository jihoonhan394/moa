-- 위키 서식(템플릿). 부서장(공간 관리자)이 공간별 서식을 만들고, 편집자가 새 문서에서 불러온다.
CREATE TABLE wiki_templates (
  id                 UUID PRIMARY KEY,
  tenant_id          UUID NOT NULL REFERENCES tenants (id),
  space_id           UUID NOT NULL REFERENCES wiki_spaces (id) ON DELETE CASCADE,
  name               VARCHAR(200) NOT NULL,
  content            TEXT NOT NULL,
  created_by_user_id UUID REFERENCES managed_users (id) ON DELETE SET NULL,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_wiki_templates_space ON wiki_templates (tenant_id, space_id);
