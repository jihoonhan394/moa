-- 로그인 키를 이메일로 전환(V53)하면서 username은 '표시 이름(핸들)'이 됐다 — 동명이인(같은 표시 이름)이
-- 있을 수 있으므로 기관별 username 유일 제약을 해제한다. 유일 키는 이메일(ux_managed_users_tenant_email 유지).
ALTER TABLE managed_users DROP CONSTRAINT IF EXISTS ux_managed_users_tenant_username;
