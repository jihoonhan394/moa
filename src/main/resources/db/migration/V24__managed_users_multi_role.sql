-- 다중역할(capability) 전환: 한 계정이 여러 관리 권한(기관 관리자·자원 관리자 등)을 동시에 가질 수
-- 있도록 단일 role 컬럼 → user_roles 조인 테이블로 옮긴다. 작은 회사는 한 명이 여러 권한, 크면 팀별 분리.
CREATE TABLE user_roles (
  user_id UUID NOT NULL REFERENCES managed_users (id) ON DELETE CASCADE,
  role    VARCHAR(20) NOT NULL,
  PRIMARY KEY (user_id, role),
  CONSTRAINT ck_user_roles_role
    CHECK (role IN ('SYSTEM_ADMIN', 'TENANT_ADMIN', 'ASSET_MANAGER', 'USER'))
);

-- 기존 단일 역할을 그대로 한 행씩 옮긴다(백필).
INSERT INTO user_roles (user_id, role)
SELECT id, role FROM managed_users;

-- 단일 role 컬럼 제거(연결된 체크 제약도 함께 제거됨). 이후 역할은 user_roles가 원본.
ALTER TABLE managed_users DROP COLUMN role;
