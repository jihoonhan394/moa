-- 로그인 키를 이메일로 전환하면서 username은 '표시 이름(핸들)'이 된다(로그인 ID 아님).
-- 초대 기반으로 username = 이메일이 들어갔던 기존 계정을 표시 이름(name)으로 정정한다.
-- 이메일은 기관 내 유일 키로 그대로 유지되므로 로그인에는 영향 없다. name은 NOT NULL이라 항상 존재.
UPDATE managed_users
   SET username = name
 WHERE username LIKE '%@%';
