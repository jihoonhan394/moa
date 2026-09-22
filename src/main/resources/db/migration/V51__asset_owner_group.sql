-- 자산(서버) 소유팀(그룹). 인프라 관리자가 서버를 "이 팀 소유"로 배정하면, 그 팀원은 개인/그룹 권한과
-- 별개로 팀 소유 서버에 접근(연결)할 수 있고, 인프라 관리자는 전체를 계속 관리한다(오버사이트).
-- 위키·솔루션·인벤토리의 '자원→그룹 소유' 패턴을 자산으로 일반화. NULL=소유팀 없음(기존 권한 모델만).
ALTER TABLE assets
  ADD COLUMN owner_group_id UUID REFERENCES access_groups (id);
