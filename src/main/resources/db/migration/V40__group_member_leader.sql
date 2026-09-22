-- 그룹 멤버에 '부서장' 표시 추가. 부서장은 부서(그룹)의 책임자로, 위키 공간 관리 등 위임 거버넌스의 기준점이 된다.
ALTER TABLE user_group_members ADD COLUMN leader BOOLEAN NOT NULL DEFAULT FALSE;
