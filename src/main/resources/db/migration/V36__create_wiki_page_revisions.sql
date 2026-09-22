-- 위키 버전 이력. 문서 수정 시 직전 상태를 스냅샷으로 남겨 이력 조회·복원을 지원한다.
CREATE TABLE wiki_page_revisions (
  id                UUID PRIMARY KEY,
  tenant_id         UUID NOT NULL REFERENCES tenants (id),
  page_id           UUID NOT NULL REFERENCES wiki_pages (id) ON DELETE CASCADE,
  title             VARCHAR(200) NOT NULL,
  content           TEXT NOT NULL,
  edited_by_user_id UUID REFERENCES managed_users (id) ON DELETE SET NULL,
  created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_wiki_revisions_page ON wiki_page_revisions (tenant_id, page_id, created_at DESC);
