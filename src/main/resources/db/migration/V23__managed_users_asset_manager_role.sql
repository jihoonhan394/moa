-- 자원 관리자(ASSET_MANAGER) 역할 도입: 서버·솔루션·자산·자격증명 관리 + 접근 부여를
-- 기관 관리자(TENANT_ADMIN)에서 분리한 신규 역할. role 체크 제약에 값을 추가한다.
ALTER TABLE managed_users DROP CONSTRAINT IF EXISTS ck_managed_users_role;
ALTER TABLE managed_users
  ADD CONSTRAINT ck_managed_users_role
  CHECK (role IN ('SYSTEM_ADMIN', 'TENANT_ADMIN', 'ASSET_MANAGER', 'USER'));
