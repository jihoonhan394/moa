-- 퇴사 회수 두 단계: 회수 확인 대기(RETURNED).
--
-- V73은 퇴사 처리가 실물 확인 없이 "창고 입고"를 적지 않게 했다(RETURN_PENDING). 그런데
-- 실물을 받은 사람(부서장 등)이 보관 화면에서 창고 입고를 누르면 그대로 장부가 닫혔다.
-- 퇴사는 정산이 걸린 순간이라 "받았다"는 한쪽 주장을 장부를 책임지는 자산 관리자가 한 번 더
-- 확인해야 한다.
--
-- 이제 받은 사람은 회수 완료만 표시하고(RETURN_PENDING → RETURNED), 자산 관리자의 회수 완료
-- 처리가 있어야 구간이 닫히고 AVAILABLE이 된다. RETURNED 동안 배정과 보관 구간은 퇴사자에게
-- 열린 채로 두며, 다른 사람에게 배정할 수 없다.
ALTER TABLE inventory_items DROP CONSTRAINT ck_inventory_items_status;

ALTER TABLE inventory_items ADD CONSTRAINT ck_inventory_items_status
    CHECK (status IN ('AVAILABLE', 'ASSIGNED', 'RETURN_PENDING', 'RETURNED', 'RETIRED'));
