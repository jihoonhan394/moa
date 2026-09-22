-- 서버 하드웨어/사양(인프라 관리자 수동 입력, 서버 상세·접속 화면에 표시). 자유 텍스트.
ALTER TABLE assets ADD COLUMN cpu VARCHAR(100);
ALTER TABLE assets ADD COLUMN ram VARCHAR(50);
ALTER TABLE assets ADD COLUMN disk VARCHAR(100);
ALTER TABLE assets ADD COLUMN hw_model VARCHAR(100);
