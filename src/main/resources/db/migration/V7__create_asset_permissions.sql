-- 그룹별 자산 접근 권한. 프로토콜 단위로 부여한다.
-- protocol을 NULL 허용으로 두면 UNIQUE가 NULL 중복을 허용해 "기본 프로토콜 접근" 중복을 막지 못하므로,
-- 'DEFAULT' 명시값을 사용해 유니크 무결성을 보장한다(설계 D6).
CREATE TABLE asset_permissions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  group_id UUID NOT NULL,
  asset_id UUID NOT NULL,
  protocol VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_asset_permissions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  CONSTRAINT fk_asset_permissions_group FOREIGN KEY (group_id) REFERENCES access_groups (id),
  CONSTRAINT fk_asset_permissions_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
  CONSTRAINT uq_asset_permissions UNIQUE (tenant_id, group_id, asset_id, protocol),
  CONSTRAINT ck_asset_permissions_protocol
    CHECK (protocol IN ('DEFAULT', 'SSH', 'RDP', 'HTTP', 'HTTPS', 'WEB'))
);

-- "내 접속 가능 자산" 조회와 접속 전 권한 검사에 사용.
CREATE INDEX idx_asset_permissions_tenant_group ON asset_permissions (tenant_id, group_id);
CREATE INDEX idx_asset_permissions_tenant_asset ON asset_permissions (tenant_id, asset_id);
