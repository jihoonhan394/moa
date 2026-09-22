-- assets 멀티테넌트 확장. tenant_id는 기존 데이터가 있는 테이블이므로 3단계로 NOT NULL 부여한다.
-- 1) nullable 추가 → 2) 기본 테넌트(MOA)로 backfill → 3) NOT NULL 승격.
ALTER TABLE assets ADD COLUMN tenant_id UUID;
UPDATE assets SET tenant_id = '00000000-0000-0000-0000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE assets ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE assets ADD CONSTRAINT fk_assets_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);

-- "내 접속 가능 자산"/목록/카운트가 tenant_id를 선두로 조회하므로 복합 인덱스로 격리 조회를 지원한다.
CREATE INDEX idx_assets_tenant_type ON assets (tenant_id, asset_type);
CREATE INDEX idx_assets_tenant_status ON assets (tenant_id, status);
