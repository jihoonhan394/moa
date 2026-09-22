-- 솔루션 카테고리(관리형 트리, 도메인 SOLUTION). 업무영역 등 그룹핑용. SolutionType(기술 유형)과 별개.
ALTER TABLE managed_solutions ADD COLUMN category VARCHAR(100);
