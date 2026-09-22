-- 자산 라이프사이클: 구매·보증·리스 만기. 보증/리스 만기는 기존 만료 통합 대시보드·알림에 자동 반영된다.
-- (감가상각·매입가 등 재무 계산은 ERP 영역이라 넣지 않는다. 여기는 '만기 추적'까지.)
ALTER TABLE inventory_items ADD COLUMN purchase_date DATE;
ALTER TABLE inventory_items ADD COLUMN warranty_ends DATE;
ALTER TABLE inventory_items ADD COLUMN lease_ends    DATE;
