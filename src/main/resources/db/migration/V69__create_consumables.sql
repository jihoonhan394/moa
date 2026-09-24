-- 소모품. A4용지·음료수처럼 "떨어지면 다시 사는" 물건.
--
-- inventory_items(개체 추적형)와 성격이 정반대라 별도 테이블로 둔다. 인벤토리는 1행=1개를
-- 시리얼로 좇지만, 소모품은 개체를 좇지 않고 "얼마나 자주 사는가"만 본다.
--
-- 이 단계(4a)에서는 재고 수량을 다루지 않는다. 알림 문구가 "현재 재고를 확인해주세요"인
-- 것 자체가 시스템이 재고를 몰라도 된다는 뜻이고, 입출고를 전부 기록하게 하면 대부분
-- 안 해서 오히려 숫자를 못 믿게 된다. 주문은 돈이 나가는 행위라 기록될 가능성이 높다.
CREATE TABLE consumable_items (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES tenants (id),
    name            VARCHAR(100) NOT NULL,
    category        VARCHAR(100),
    -- 세는 단위(박스·개·팩). 바꾸면 과거 주문량의 뜻이 달라져 소비율이 무의미해진다.
    unit            VARCHAR(20),
    -- 담당자가 아는 예상 주문 주기(일). 주문이 3회 쌓이기 전에도 알림이 돌게 하는 값이다.
    -- 비워 두면 실측이 쌓일 때까지 알림이 없다.
    cycle_days      INT,
    -- 알림 수신자 판정(소유팀 리더 + 자산 관리자).
    owner_group_id  UUID REFERENCES access_groups (id) ON DELETE SET NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    note            VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_consumable_items_tenant_name UNIQUE (tenant_id, name)
);

CREATE INDEX idx_consumable_items_tenant ON consumable_items (tenant_id, active, name);

-- 주문 이력. 이 날짜들의 '간격'과 '수량'이 예측의 전부다.
--
--   소비율 = 주문i.수량 ÷ (주문i+1.날짜 − 주문i.날짜)
--
-- 한 번 주문한 양은 다음 주문 때까지 소비된 양이라는 관찰에서 나온다(재고가 바닥날 즈음
-- 주문한다는 전제). 수량이 전부 같으면 결과가 '간격 평균'과 같아지고, 다를 때만 보정된다.
CREATE TABLE consumable_orders (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants (id),
    item_id     UUID NOT NULL REFERENCES consumable_items (id) ON DELETE CASCADE,
    ordered_on  DATE NOT NULL,
    quantity    INT NOT NULL DEFAULT 1,
    note        VARCHAR(500),
    created_by  UUID REFERENCES managed_users (id) ON DELETE SET NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_consumable_orders_item ON consumable_orders (tenant_id, item_id, ordered_on);
