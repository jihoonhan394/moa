-- 실물·SW 인벤토리 도메인(제품비전 §1: 실물·SW = 배정·추적·회수, 자산 관리자 소유).
-- 노트북·모니터 등 실물과 SW 라이선스를 등록하고 사용자에게 배정·회수하며 만료를 추적한다.
CREATE TABLE inventory_items (
  id               UUID PRIMARY KEY,
  tenant_id        UUID NOT NULL REFERENCES tenants (id),
  name             VARCHAR(100) NOT NULL,
  item_type        VARCHAR(20) NOT NULL,
  category         VARCHAR(100),
  serial_no        VARCHAR(100),
  status           VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  assigned_user_id UUID REFERENCES managed_users (id) ON DELETE SET NULL,
  expires_at       DATE,
  note             VARCHAR(500),
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_inventory_items_tenant_name UNIQUE (tenant_id, name),
  CONSTRAINT ck_inventory_items_type CHECK (item_type IN ('PHYSICAL', 'SOFTWARE')),
  CONSTRAINT ck_inventory_items_status CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'RETIRED'))
);

CREATE INDEX idx_inventory_items_tenant ON inventory_items (tenant_id);
CREATE INDEX idx_inventory_items_assignee ON inventory_items (tenant_id, assigned_user_id);

-- 데모(기본) 기관에 인벤토리 기능을 켜 둔다. 다른 기관은 기관 상세에서 토글.
INSERT INTO tenant_features (tenant_id, feature)
VALUES ('00000000-0000-0000-0000-000000000001', 'INVENTORY')
ON CONFLICT DO NOTHING;
