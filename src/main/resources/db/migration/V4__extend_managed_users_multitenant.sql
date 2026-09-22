-- T02: managed_users 멀티테넌트/인증 확장.

-- 1) tenant_id: SYSTEM_ADMIN은 null 허용이므로 nullable 유지. 기존 행은 기본 테넌트 MOA로 backfill.
ALTER TABLE managed_users ADD COLUMN tenant_id UUID;
UPDATE managed_users
   SET tenant_id = '00000000-0000-0000-0000-000000000001'
 WHERE tenant_id IS NULL;
ALTER TABLE managed_users
  ADD CONSTRAINT fk_managed_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
CREATE INDEX idx_managed_users_tenant ON managed_users (tenant_id);

-- 2) role: nullable -> backfill(USER) -> NOT NULL + CHECK. (기존 데이터 있는 컬럼 3단계 원칙)
ALTER TABLE managed_users ADD COLUMN role VARCHAR(20);
UPDATE managed_users SET role = 'USER' WHERE role IS NULL;
ALTER TABLE managed_users ALTER COLUMN role SET NOT NULL;
ALTER TABLE managed_users
  ADD CONSTRAINT ck_managed_users_role CHECK (role IN ('SYSTEM_ADMIN', 'TENANT_ADMIN', 'USER'));

-- 3) last_login_at (운영 참고용, 선택)
ALTER TABLE managed_users ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

-- 4) email 선택값 전환. 기존 UNIQUE 유지: nullable 컬럼의 UNIQUE는 다중 NULL 허용 +
--    비NULL 값만 유니크 = "값이 있을 때만 유니크"(partial unique) 효과. 별도 인덱스 불필요.
ALTER TABLE managed_users ALTER COLUMN email DROP NOT NULL;
