-- 위키 첨부파일: 파일 본체는 서버 파일시스템(기관별 하위 디렉터리)에 저장하고, 여기엔 메타데이터만 둔다.
-- storage_path 는 저장 루트 기준 상대 경로(<tenant_id>/<id>)이며, 실제 파일명은 UUID라 경로 조작이 불가능하다.
-- 원본 파일명(filename)은 표시·다운로드용으로만 보관한다. 페이지 삭제 시 메타는 CASCADE로 정리된다.
CREATE TABLE wiki_attachments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    page_id             UUID NOT NULL REFERENCES wiki_pages (id) ON DELETE CASCADE,
    filename            VARCHAR(255) NOT NULL,
    content_type        VARCHAR(150),
    size_bytes          BIGINT NOT NULL,
    storage_path        VARCHAR(500) NOT NULL,
    uploaded_by_user_id UUID REFERENCES managed_users (id) ON DELETE SET NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_wiki_attachments_page ON wiki_attachments (tenant_id, page_id, created_at DESC);
