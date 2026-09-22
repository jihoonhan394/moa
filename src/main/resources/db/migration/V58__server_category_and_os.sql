-- 계층(트리) 도입으로 이름 유일성은 '형제(같은 부모)' 범위여야 한다. 도메인 전역 유니크는 같은 이름이
-- 다른 부모 아래 존재하는 트리(내부망›웹서버, DMZ›웹서버)를 막으므로 제거하고, 형제 유일성은
-- 앱(CategoryService.existsBy...ParentId...)에서 보장한다.
ALTER TABLE categories DROP CONSTRAINT IF EXISTS uq_categories_tenant_domain_name;

-- 서버(및 향후 솔루션) 카테고리 도메인 확장.
ALTER TABLE categories DROP CONSTRAINT IF EXISTS ck_categories_domain;
ALTER TABLE categories ADD CONSTRAINT ck_categories_domain
  CHECK (domain IN ('ASSET', 'SHARED_RESOURCE', 'SERVER', 'SOLUTION'));

-- 서버(assets)에 카테고리 경로 + OS 계열(필터·통계용). osType(자유 텍스트 상세)은 유지.
ALTER TABLE assets ADD COLUMN category VARCHAR(100);
ALTER TABLE assets ADD COLUMN os_family VARCHAR(20);
