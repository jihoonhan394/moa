-- 기관 자가 회원가입(가입 신청) 도입: managed_users.status에 PENDING(승인 대기) 허용.
-- 기존 체크 제약(ACTIVE/DISABLED)을 확장한다. PostgreSQL·H2(PostgreSQL 모드) 공용.
ALTER TABLE managed_users DROP CONSTRAINT IF EXISTS ck_managed_users_status;
ALTER TABLE managed_users
  ADD CONSTRAINT ck_managed_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED'));
