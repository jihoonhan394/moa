-- 자산 관리번호(= QR 스티커 코드).
--
-- 자산 테이블의 컬럼이 아니라 별도 테이블인 이유: 스티커를 **미리 대량 발급**해 두고 현장에서
-- 붙이기 때문이다. 발급 시점에는 어느 자산에 붙을지 모른다(item_id 가 null 인 구간이 있다).
--
-- code 는 전역 유니크다. 기관마다 따로 두면 다른 기관 스티커를 찍었을 때 "없는 코드"가 아니라
-- "내 기관의 다른 자산"이 나올 수 있다. 조회할 때 tenant_id 를 반드시 함께 검증한다.
CREATE TABLE asset_tags (
    code         VARCHAR(16)  PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    item_id      UUID,
    issued_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    issued_by    UUID,
    attached_at  TIMESTAMP WITH TIME ZONE,
    attached_by  UUID,
    CONSTRAINT fk_asset_tags_item FOREIGN KEY (item_id) REFERENCES inventory_items (id) ON DELETE SET NULL
);

-- 한 자산에 스티커가 둘 붙으면 "이 번호로 찾아 주세요"가 통하지 않는다.
-- 부분 인덱스(WHERE)를 쓰지 않는 이유: 로컬·테스트가 H2 라 지원하지 않는다. 표준대로 NULL 은
-- 서로 다른 값으로 취급되므로, 미부착 스티커(item_id IS NULL)는 얼마든지 공존한다.
CREATE UNIQUE INDEX ux_asset_tags_item ON asset_tags (item_id);

-- 실제로 나가는 질의에 맞춘 인덱스다.
--   · 미부착 목록      tenant_id = ? AND item_id IS NULL ORDER BY issued_at DESC
--   · 미부착 장수      tenant_id = ? AND item_id IS NULL
--   · 자산→번호 묶음   tenant_id = ? AND item_id IN (...)
-- 셋 다 (tenant_id, item_id) 로 좁힌 뒤 issued_at 으로 정렬한다.
-- attached_at 으로 잡으면 위 질의 중 어느 것도 타지 않는다.
CREATE INDEX ix_asset_tags_tenant_item ON asset_tags (tenant_id, item_id, issued_at);
