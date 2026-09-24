-- 자산 보관 장부(custody ledger). "지금 이 물건을 누가 갖고 있나"의 답을 구간으로 쌓는다.
--
-- 지금까지는 inventory_items.assigned_user_id 한 칸뿐이라 현재 보유자만 알 수 있었고,
-- 언제부터인지도 이전에 누구였는지도 남지 않았다. 이 표는 그 이력을 보관 구간으로 기록한다.
--
-- 핵심 설계: 보관자(holder)를 일반화한다. 사람일 수도, 창고일 수도, 고객처일 수도,
-- 그리고 다른 장비일 수도 있다(부품 장착 = 상위 장비에게 보관 이전). 그래서 납품·반입과
-- 부품 이동이 별도 테이블 없이 같은 장부에 들어온다.
--
--   WAREHOUSE    창고(사내 보관)
--   USER         사내 구성원          holder_id = managed_users.id
--   GROUP        부서                 holder_id = access_groups.id
--   CUSTOMER     고객처(납품 나감)     holder_name = 거래처 이름
--   VENDOR       수리·임대 업체        holder_name = 업체 이름
--   PARENT_ITEM  다른 장비에 장착됨    holder_id = inventory_items.id
--   DISPOSED     폐기
--
-- 현재 보유 = ended_on IS NULL. 이력 = 그 품목의 구간을 시간순으로.
-- inventory_items.assigned_user_id / status 는 활성 구간의 파생 캐시로 남는다
-- (기존 화면·퇴사 회수·팀 경계·테스트가 그대로 동작하도록).
CREATE TABLE inventory_custodies (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    item_id            UUID NOT NULL REFERENCES inventory_items (id) ON DELETE CASCADE,
    -- 이 구간이 덮는 수량. 부분 이동(RAM 2개 중 1개)을 위해 두지만 기존 자산은 전부 1이다.
    quantity           INT NOT NULL DEFAULT 1,
    holder_type        VARCHAR(20) NOT NULL,
    -- 내부 대상(USER/GROUP/PARENT_ITEM)의 ID. 외부 거래처는 이름만 남긴다.
    -- FK를 걸지 않는 이유: 한 컬럼이 세 테이블을 가리키고, 사용자가 삭제돼도
    -- "그때 누가 갖고 있었나"는 장부에 남아야 하기 때문이다.
    holder_id          UUID,
    holder_name        VARCHAR(200),
    started_on         DATE NOT NULL,
    -- NULL이면 현재 보유 중. 값이 있으면 그날 다른 보관자에게 넘어갔다.
    ended_on           DATE,
    -- 납품·대여·수리의 반납 예정일. 지났는데 ended_on이 비어 있으면 '반납 초과'다.
    expected_return_on DATE,
    reason             VARCHAR(100),
    note               VARCHAR(500),
    -- 배치(퇴사 회수 등)로 생긴 구간은 행위자를 알 수 없어 NULL이다.
    -- "누가 했나"는 감사 로그가 답하고, 이 표는 "어디에 있었나"를 답한다.
    created_by         UUID REFERENCES managed_users (id) ON DELETE SET NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 이력 조회: 품목별 시간순.
CREATE INDEX idx_inventory_custodies_item
    ON inventory_custodies (tenant_id, item_id, started_on DESC);

-- 현재 보유 조회 + 반납 초과 스캔: 열린 구간만.
CREATE INDEX idx_inventory_custodies_open
    ON inventory_custodies (tenant_id, ended_on, expected_return_on);
