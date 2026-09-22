-- 인벤토리 소유팀(그룹). 자산 관리자가 실물/SW 자산을 "이 팀 소유"로 배정하면, 그 팀원은 개인 배정과
-- 별개로 자기 팀이 소유한 자산을 볼 수 있고, 자산 관리자는 전체를 계속 관리한다(오버사이트).
-- 위키·솔루션의 '자원→그룹 소유' 패턴을 인벤토리로 일반화. NULL=소유팀 없음(자산관리자 전용, 기존 동작).
ALTER TABLE inventory_items
  ADD COLUMN owner_group_id UUID REFERENCES access_groups (id);
