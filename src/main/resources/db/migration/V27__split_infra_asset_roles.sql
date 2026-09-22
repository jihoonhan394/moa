-- 역할 분리: 통합 '자원 관리자(ASSET_MANAGER)'를 '인프라 관리자(INFRA_MANAGER)'와
-- '자산 관리자(ASSET_MANAGER = 실물·SW 인벤토리)'로 가른다. 제품비전의 도메인별 capability에 정합.
-- 현재 ASSET_MANAGER 보유자는 서버·솔루션·접속을 관리하던 사람이므로 인프라 관리자로 이관한다.
ALTER TABLE user_roles DROP CONSTRAINT IF EXISTS ck_user_roles_role;

UPDATE user_roles SET role = 'INFRA_MANAGER' WHERE role = 'ASSET_MANAGER';

ALTER TABLE user_roles
  ADD CONSTRAINT ck_user_roles_role
    CHECK (role IN ('SYSTEM_ADMIN', 'TENANT_ADMIN', 'INFRA_MANAGER', 'ASSET_MANAGER', 'USER'));
