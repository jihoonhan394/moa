-- 솔루션 소유팀(그룹). 인프라팀이 솔루션을 등록하고 "이 솔루션은 이 팀 소유"로 배정하면,
-- 그 그룹의 팀원은 별도 개인 배정 없이도 운영(제어)할 수 있고, 인프라팀은 전체를 계속 본다(장애 대응).
-- 위키의 '공간→그룹 소유' 패턴을 솔루션으로 일반화한 것. NULL=소유팀 없음(인프라 전용, 기존 동작 유지).
ALTER TABLE managed_solutions
  ADD COLUMN owner_group_id UUID REFERENCES access_groups (id);
