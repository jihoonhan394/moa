-- T17: 묶음 권한(Permission Set). 명명된 권한 = 여러 (자산, 액션) 엔트리의 묶음.
-- 그룹 부착(주력) + 사용자 부착(일시 예외, 만료). 접근 판정 전환은 T18, UI는 T19.

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants (id),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_permissions_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_permissions_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 묶음 내용: 자산 × 액션. action은 asset_permissions.protocol과 동일 도메인(PermissionProtocol).
CREATE TABLE permission_entries (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants (id),
    permission_id UUID NOT NULL REFERENCES permissions (id),
    asset_id      UUID NOT NULL REFERENCES assets (id),
    action        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_permission_entries UNIQUE (tenant_id, permission_id, asset_id, action),
    CONSTRAINT ck_permission_entries_action CHECK (action IN ('DEFAULT', 'SSH', 'RDP', 'HTTP', 'HTTPS', 'WEB'))
);
CREATE INDEX idx_permission_entries_permission ON permission_entries (permission_id);
CREATE INDEX idx_permission_entries_asset ON permission_entries (tenant_id, asset_id);

-- 그룹 부착(주력): 그룹 멤버 전원이 해당 권한을 받는다.
CREATE TABLE permission_group_assignments (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants (id),
    permission_id UUID NOT NULL REFERENCES permissions (id),
    group_id      UUID NOT NULL REFERENCES access_groups (id),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_permission_group UNIQUE (tenant_id, permission_id, group_id)
);
CREATE INDEX idx_permission_group_group ON permission_group_assignments (tenant_id, group_id);

-- 사용자 부착(일시 예외): expires_at NULL=무기한, 값=만료 시각(경과 후 무효 — 판정은 T18).
CREATE TABLE permission_user_assignments (
    id            UUID PRIMARY KEY,
    tenant_id     UUID NOT NULL REFERENCES tenants (id),
    permission_id UUID NOT NULL REFERENCES permissions (id),
    user_id       UUID NOT NULL REFERENCES managed_users (id),
    expires_at    TIMESTAMP WITH TIME ZONE,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_permission_user UNIQUE (tenant_id, permission_id, user_id)
);
CREATE INDEX idx_permission_user_user ON permission_user_assignments (tenant_id, user_id);
