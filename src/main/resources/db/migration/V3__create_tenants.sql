-- T01: 멀티테넌트 기반. 모든 업무 데이터 격리의 정점 테이블.
CREATE TABLE tenants (
  id UUID PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  code VARCHAR(50) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 기본 테넌트 MOA 시드. id는 고정 UUID로 박아 이후 V4/V6 backfill과 코드가 결정적으로 참조한다.
-- (Tenant.DEFAULT_TENANT_ID = 00000000-0000-0000-0000-000000000001)
INSERT INTO tenants (id, name, code, status, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'MOA', 'MOA', 'ACTIVE',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
