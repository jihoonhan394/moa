-- 기관 공지사항 게시판. 기관 관리자(TENANT_ADMIN)가 작성·수정·삭제, 기관 전원이 열람.
-- 본문은 순수 텍스트로 저장하고 화면에서 th:text로만 출력한다(XSS 방지, HTML 미허용).
CREATE TABLE notices (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenants (id),
  title       VARCHAR(200) NOT NULL,
  body        VARCHAR(4000) NOT NULL,
  author_id   UUID,
  author_name VARCHAR(100),
  pinned      BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notices_tenant ON notices (tenant_id, pinned, created_at);
