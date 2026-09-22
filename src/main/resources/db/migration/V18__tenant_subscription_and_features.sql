-- T06: 기관 구독(사용기간) + 기능(모듈) 엔타이틀먼트.

-- 1) 구독 기간(널=무제한). H2 호환 위해 컬럼별 ALTER.
ALTER TABLE tenants ADD COLUMN subscription_start DATE;
ALTER TABLE tenants ADD COLUMN subscription_end DATE;

-- 2) 기관별 사용 가능 기능 집합.
CREATE TABLE tenant_features (
  tenant_id UUID NOT NULL REFERENCES tenants (id),
  feature VARCHAR(30) NOT NULL,
  CONSTRAINT pk_tenant_features PRIMARY KEY (tenant_id, feature)
);

-- 3) 기본(데모) 기관은 전 기능 활성.
INSERT INTO tenant_features (tenant_id, feature) VALUES
  ('00000000-0000-0000-0000-000000000001', 'ASSETS'),
  ('00000000-0000-0000-0000-000000000001', 'SERVER_ACCESS'),
  ('00000000-0000-0000-0000-000000000001', 'SOLUTIONS'),
  ('00000000-0000-0000-0000-000000000001', 'CREDENTIALS');
