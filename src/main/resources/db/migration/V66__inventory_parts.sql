-- 자산 부품(구성품). 새 테이블을 만들지 않는다 — 부품도 그냥 자산이고, 다른 점은
-- "어느 장비에 들어가 있나" 하나뿐이다. RAM·SSD·OS 라이선스가 전부 여기에 해당한다.
--
--   parent_item_id : 장착된 상위 장비. 활성 PARENT_ITEM 보관 구간의 파생 캐시다
--                    (inventory_custodies가 이력을, 이 컬럼이 현재 상태를 담당).
--   quantity       : 같은 부품 여러 개를 한 행으로 (RAM 32GB 2개).
--                    부분 이동은 이 수량을 쪼개 새 행을 만든다.
--                    시리얼이 있는 물건은 쪼갤 수 없으므로 서비스가 1로 강제한다.
--
-- 기존 자산은 전부 parent_item_id=NULL, quantity=1이라 동작이 달라지지 않는다.
ALTER TABLE inventory_items
    ADD COLUMN parent_item_id UUID REFERENCES inventory_items (id) ON DELETE SET NULL;

ALTER TABLE inventory_items
    ADD COLUMN quantity INT NOT NULL DEFAULT 1;

-- 상위 장비의 구성품 조회.
CREATE INDEX idx_inventory_items_parent ON inventory_items (tenant_id, parent_item_id);

-- 이름 유니크를 상위 자산에만 적용해야 한다.
-- "RAM 32GB"는 노트북 다섯 대에 각각 들어갈 수 있고 그게 정상이다. 기존 제약
-- (tenant_id, name)은 최상위 자산 대장의 중복 등록을 막으려던 규칙이었고, 부분 이동으로
-- 행이 갈라질 때도 이 제약이 걸림돌이 된다.
--
-- 다만 "부품일 때만 제외"는 부분 유니크 인덱스(WHERE 절)가 필요한데 H2가 지원하지 않는다
-- (운영은 PostgreSQL이지만 로컬·테스트가 H2라 마이그레이션은 양쪽에서 돌아야 한다).
-- 그래서 제약을 걷어내고 판정을 서비스 계층으로 옮긴다 —
-- InventoryItemService.requireUniqueName이 상위 자산에만 중복을 막는다.
--
-- 잃는 것: 앱을 거치지 않는 직접 INSERT는 이제 중복 이름을 만들 수 있다.
-- 감수하는 이유: 이 제약은 데이터 정합성(FK·NOT NULL)이 아니라 입력 편의 규칙이고,
-- 자산 대장은 CSV 임포트를 포함해 전부 서비스를 거쳐 들어온다.
ALTER TABLE inventory_items DROP CONSTRAINT uq_inventory_items_tenant_name;
