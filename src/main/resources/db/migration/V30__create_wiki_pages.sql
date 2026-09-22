-- 위키 도메인(제품비전 §1: 위키 = 편집). 기관 내부 문서를 사용자들이 협업 작성·편집한다.
-- 내용은 평문으로 저장하고 화면에선 이스케이프해 표시한다(XSS 방지 — HTML 렌더링 안 함).
CREATE TABLE wiki_pages (
  id                  UUID PRIMARY KEY,
  tenant_id           UUID NOT NULL REFERENCES tenants (id),
  title               VARCHAR(200) NOT NULL,
  content             TEXT NOT NULL,
  author_user_id      UUID REFERENCES managed_users (id) ON DELETE SET NULL,
  updated_by_user_id  UUID REFERENCES managed_users (id) ON DELETE SET NULL,
  created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_wiki_pages_tenant_updated ON wiki_pages (tenant_id, updated_at DESC);

-- 데모(기본) 기관에 위키 기능을 켜 둔다.
INSERT INTO tenant_features (tenant_id, feature)
VALUES ('00000000-0000-0000-0000-000000000001', 'WIKI')
ON CONFLICT DO NOTHING;
