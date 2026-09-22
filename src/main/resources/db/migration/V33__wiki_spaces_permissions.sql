-- 위키 v2: 공간(Space) + 권한. 페이지는 공간에 속하고, 권한은 공간 단위로 그룹/사용자/전체에 부여한다.
-- fail-closed: 권한이 없으면 아무도 못 본다. 기관 관리자는 앱 레벨에서 전 공간 백업 접근.
CREATE TABLE wiki_spaces (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenants (id),
  parent_id   UUID REFERENCES wiki_spaces (id) ON DELETE CASCADE,  -- null=최상위 폴더
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_wiki_spaces_tenant_parent ON wiki_spaces (tenant_id, parent_id);

CREATE TABLE wiki_space_permissions (
  id           UUID PRIMARY KEY,
  tenant_id    UUID NOT NULL REFERENCES tenants (id),
  space_id     UUID NOT NULL REFERENCES wiki_spaces (id) ON DELETE CASCADE,
  subject_type VARCHAR(10) NOT NULL,   -- ALL / GROUP / USER
  subject_id   UUID,                    -- ALL이면 null
  access_level VARCHAR(10) NOT NULL,   -- VIEW / EDIT / MANAGE
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_wiki_perm_subject CHECK (subject_type IN ('ALL', 'GROUP', 'USER')),
  CONSTRAINT ck_wiki_perm_level CHECK (access_level IN ('VIEW', 'EDIT', 'MANAGE'))
);

CREATE INDEX idx_wiki_space_perms_space ON wiki_space_permissions (space_id);
CREATE INDEX idx_wiki_space_perms_subject ON wiki_space_permissions (tenant_id, subject_type, subject_id);

ALTER TABLE wiki_pages ADD COLUMN space_id UUID REFERENCES wiki_spaces (id);

-- 기존 평면 위키 → 기본 기관의 '전사 위키' 공간으로 이관(전원 열람·편집 = 종전 동작 유지).
INSERT INTO wiki_spaces (id, tenant_id, name, description, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000001',
        '전사 위키', '전 직원 공용 위키', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO wiki_space_permissions (id, tenant_id, space_id, subject_type, subject_id, access_level, created_at)
VALUES ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-0000000000a1', 'ALL', NULL, 'EDIT', CURRENT_TIMESTAMP);

UPDATE wiki_pages SET space_id = '00000000-0000-0000-0000-0000000000a1'
WHERE tenant_id = '00000000-0000-0000-0000-000000000001' AND space_id IS NULL;
