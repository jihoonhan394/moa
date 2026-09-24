-- 소모품 요청. 일반 사용자가 "떨어졌어요"를 남기는 곳.
--
-- 자산 담당자는 모든 선반을 볼 수 없다. 떨어진 걸 제일 먼저 아는 사람은 쓰려다 없는 걸
-- 발견한 직원이고, 지금 그 정보는 메신저에서 증발한다. 이 표가 그걸 받는다.
--
-- 이미 등록된 품목만 고를 수 있게 FK로 묶는다(자유 입력을 열면 마스터가 오염된다).
-- 목록에 없는 것이 필요하면 기관이 "기타(비고에 적어주세요)" 품목을 하나 두면 된다.
--
-- 수량은 받지 않는다. 요청은 "떨어졌다"는 신호이고 몇 개 살지는 담당자가 정한다 —
-- 입력 칸이 적을수록 실제로 쓴다.
CREATE TABLE consumable_requests (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    item_id         UUID NOT NULL REFERENCES consumable_items (id) ON DELETE CASCADE,
    requested_by    UUID NOT NULL REFERENCES managed_users (id),
    -- "제로 콜라가 떨어졌는데 부탁드려요" — 품목이 '음료수'일 때 세부를 전달하는 칸.
    note            VARCHAR(500),
    -- REQUESTED → ACKNOWLEDGED → FULFILLED / REJECTED / HELD
    status          VARCHAR(20) NOT NULL,
    decided_by      UUID REFERENCES managed_users (id) ON DELETE SET NULL,
    decided_at      TIMESTAMP WITH TIME ZONE,
    -- 거절·보류는 사유가 필수다. 왜 안 됐는지 모르면 요청자는 같은 요청을 반복한다.
    decision_reason VARCHAR(500),
    -- 보류 재검토 예정일. 없으면 보류가 영원히 남아 목록이 쓰레기가 된다.
    review_on       DATE,
    -- 이 요청 때문에 실제로 주문한 건. 연결해 두면 주문 이력이 저절로 쌓인다.
    order_id        UUID REFERENCES consumable_orders (id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_consumable_requests_open
    ON consumable_requests (tenant_id, status, created_at);
CREATE INDEX idx_consumable_requests_item
    ON consumable_requests (tenant_id, item_id, status);
CREATE INDEX idx_consumable_requests_user
    ON consumable_requests (tenant_id, requested_by, created_at);
