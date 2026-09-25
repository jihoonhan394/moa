-- 퇴사 반납 대기 상태.
--
-- 지금까지 퇴사 처리는 배정된 자산을 곧바로 AVAILABLE로 바꾸고 장부에 "창고 입고" 구간을
-- 열었다. 아무도 물건을 보지 않았는데 장부가 "창고에 있음"이라고 기록한 것이다. 게다가
-- AVAILABLE이라 다음 사람에게 배정까지 가능했다 — 받으러 가면 아무것도 없는 상태로.
--
-- 장부의 값어치는 확인된 사실에 있다. 확인하지 않은 기록은 없는 것보다 나쁘다. 확신을
-- 가지고 틀리기 때문이다. 0.7.20의 인수 확인(confirmed_at)이 "관리자가 일방적으로 쓴
-- 장부는 한쪽 주장일 뿐"이라서 생겼는데, 퇴사 경로가 그 장치를 통째로 우회하고 있었다.
-- 하필 퇴사는 정산이 걸려 분쟁 가능성이 가장 높은 순간이다.
--
-- 이제 퇴사 처리는 상태만 RETURN_PENDING으로 바꾼다. 보관 구간은 그 사람에게 열린 채로
-- 둔다 — 물건이 실제로 거기 있으니 그것이 사실이다. 자산 관리자가 실물을 확인하고 창고
-- 입고를 기록할 때 비로소 구간이 닫히고 AVAILABLE이 된다.
ALTER TABLE inventory_items DROP CONSTRAINT ck_inventory_items_status;

ALTER TABLE inventory_items ADD CONSTRAINT ck_inventory_items_status
    CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'RETURN_PENDING', 'RETIRED'));

-- 반납 대기 조회(관리자 화면의 미반납 목록).
CREATE INDEX idx_inventory_items_return_pending
    ON inventory_items (tenant_id, status);
