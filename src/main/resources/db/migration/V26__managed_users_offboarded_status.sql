-- 퇴사 라이프사이클: managed_users.status에 OFFBOARDED(퇴사, 종료 상태) 허용.
-- 로그인은 ACTIVE만 허용되므로 OFFBOARDED는 자동으로 로그인 차단된다.
ALTER TABLE managed_users DROP CONSTRAINT IF EXISTS ck_managed_users_status;
ALTER TABLE managed_users
  ADD CONSTRAINT ck_managed_users_status CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED', 'OFFBOARDED'));
