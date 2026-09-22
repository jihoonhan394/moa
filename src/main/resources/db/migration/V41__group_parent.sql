-- 그룹(조직) 트리 구조: 상위 그룹 참조. NULL=최상위. 자기참조 FK, 순환은 애플리케이션에서 차단.
ALTER TABLE access_groups ADD COLUMN parent_id UUID REFERENCES access_groups (id);
CREATE INDEX idx_access_groups_parent ON access_groups (tenant_id, parent_id);
