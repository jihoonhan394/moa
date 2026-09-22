-- 위키 편의기능: 라벨(분류), 댓글, 즐겨찾기(북마크).
CREATE TABLE wiki_page_labels (
  id         UUID PRIMARY KEY,
  tenant_id  UUID NOT NULL REFERENCES tenants (id),
  page_id    UUID NOT NULL REFERENCES wiki_pages (id) ON DELETE CASCADE,
  label      VARCHAR(50) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_wiki_labels UNIQUE (page_id, label)
);
CREATE INDEX idx_wiki_labels_tenant_label ON wiki_page_labels (tenant_id, label);

CREATE TABLE wiki_comments (
  id             UUID PRIMARY KEY,
  tenant_id      UUID NOT NULL REFERENCES tenants (id),
  page_id        UUID NOT NULL REFERENCES wiki_pages (id) ON DELETE CASCADE,
  author_user_id UUID REFERENCES managed_users (id) ON DELETE SET NULL,
  content        VARCHAR(2000) NOT NULL,
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_wiki_comments_page ON wiki_comments (tenant_id, page_id, created_at);

CREATE TABLE wiki_favorites (
  id         UUID PRIMARY KEY,
  tenant_id  UUID NOT NULL REFERENCES tenants (id),
  user_id    UUID NOT NULL REFERENCES managed_users (id) ON DELETE CASCADE,
  page_id    UUID NOT NULL REFERENCES wiki_pages (id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_wiki_favorites UNIQUE (user_id, page_id)
);
CREATE INDEX idx_wiki_favorites_user ON wiki_favorites (tenant_id, user_id);
