-- 자산 카테고리를 계층(트리)으로. 최상위는 유형(실물/SW), 그 아래 다단계 하위(사무기기 > 컴퓨터 …).
--   parent_id : 자기참조 트리(루트는 NULL)
--   item_type : ASSET 도메인 노드의 유형 귀속(PHYSICAL/SOFTWARE). SHARED_RESOURCE는 NULL.
ALTER TABLE categories ADD COLUMN parent_id UUID REFERENCES categories (id);
ALTER TABLE categories ADD COLUMN item_type VARCHAR(20);
CREATE INDEX idx_categories_parent ON categories (parent_id);

-- 직전 버전(V56)에서 lazy 시드된 '평면' 자산 카테고리는 새 트리 모델과 맞지 않으므로 제거하고
-- 앱(CategoryService)이 유형별 트리로 재시드하게 한다. 인벤토리 항목의 category는 경로 '문자열'로
-- 저장되므로(카테고리 행 참조 아님) 이 삭제는 기존 항목 데이터에 영향을 주지 않는다.
DELETE FROM categories WHERE domain = 'ASSET';
