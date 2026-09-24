-- 인수 확인. 받은 사람이 "받았다"를 남기는 칸.
--
-- 지금까지 보관 장부는 관리자가 일방적으로 썼다. 받은 사람의 기록이 없으니, 나중에
-- "저는 그 노트북 받은 적 없는데요"가 나오면 장부는 한쪽 주장일 뿐이다. 퇴사 정산처럼
-- 돈이 걸린 자리에서 특히 곤란하다.
--
-- 사용자 보관 구간(holder_type='USER')에만 의미가 있다. 창고·고객처 구간은 확인할
-- 사람이 없으므로 NULL로 남는다.
ALTER TABLE inventory_custodies ADD COLUMN confirmed_at TIMESTAMP WITH TIME ZONE;

-- 미확인 구간 조회(관리자 화면의 '확인 대기' 배지, 리마인드 대상).
CREATE INDEX idx_inventory_custodies_unconfirmed
    ON inventory_custodies (tenant_id, holder_type, confirmed_at);
