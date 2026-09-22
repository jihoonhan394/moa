-- 테넌트 내 권한 부여 주체(access_groups)와 사용자-그룹 N:M 매핑(user_group_members).
-- 신규 테이블이므로 backfill 없이 바로 제약을 건다.
CREATE TABLE access_groups (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(1000),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_access_groups_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  CONSTRAINT uq_access_groups_tenant_name UNIQUE (tenant_id, name),
  CONSTRAINT ck_access_groups_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE user_group_members (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,
  group_id UUID NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_ugm_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  CONSTRAINT fk_ugm_user FOREIGN KEY (user_id) REFERENCES managed_users (id),
  CONSTRAINT fk_ugm_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
  CONSTRAINT uq_ugm_tenant_user_group UNIQUE (tenant_id, user_id, group_id)
);

CREATE INDEX idx_ugm_tenant_user ON user_group_members (tenant_id, user_id);
CREATE INDEX idx_ugm_tenant_group ON user_group_members (tenant_id, group_id);
