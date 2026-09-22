-- 솔루션 운영 콘솔: 위키 매뉴얼 연결 + 유지보수 업체 연락처(우리 솔루션이 아닐 수 있으므로).
-- 담당자는 maintenance_owners, 작업 이력은 maintenance_windows를 재사용(새 테이블 없음).
ALTER TABLE managed_solutions ADD COLUMN wiki_space_id  UUID;         -- 기동 매뉴얼·문서 모음(위키 공간)
ALTER TABLE managed_solutions ADD COLUMN vendor_name    VARCHAR(200); -- 유지보수 업체명
ALTER TABLE managed_solutions ADD COLUMN vendor_contact VARCHAR(300); -- 업체 연락처(전화/이메일/담당)
ALTER TABLE managed_solutions ADD COLUMN vendor_note    VARCHAR(1000);-- 계약·지원범위·SLA 등 메모
