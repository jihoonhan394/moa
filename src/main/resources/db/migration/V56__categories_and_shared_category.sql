-- 관리형 카테고리(자산 관리자가 CRUD). 자산(ASSET)·공유자산(SHARED_RESOURCE) 두 도메인을 한 테이블로.
-- 기본값 시드는 앱(CategoryService)에서 lazy 생성(UUID는 앱에서 발급 → gen_random_uuid 등 DB 종속 함수 회피).
CREATE TABLE categories (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenants (id),
  domain      VARCHAR(30) NOT NULL,
  name        VARCHAR(100) NOT NULL,
  sort_order  INTEGER NOT NULL DEFAULT 0,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_categories_domain CHECK (domain IN ('ASSET', 'SHARED_RESOURCE')),
  CONSTRAINT uq_categories_tenant_domain_name UNIQUE (tenant_id, domain, name)
);
CREATE INDEX idx_categories_tenant_domain ON categories (tenant_id, domain);

-- 공유자산: 고정 enum(type) → 관리형 문자열(category). 기존 값은 한글 라벨로 백필한 뒤 type 컬럼 제거.
ALTER TABLE shared_resources ADD COLUMN category VARCHAR(100);
UPDATE shared_resources SET category = CASE type
  WHEN 'VEHICLE' THEN '차량'
  WHEN 'ROOM' THEN '회의실'
  WHEN 'SEAT' THEN '좌석'
  ELSE '기타' END;
ALTER TABLE shared_resources DROP CONSTRAINT IF EXISTS ck_shared_resources_type;
ALTER TABLE shared_resources DROP COLUMN type;
