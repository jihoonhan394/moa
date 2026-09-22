-- 문제 발생 시 연락(메일/전화) 대응을 위해 사용자 전화번호 컬럼을 추가한다.
-- 기존 계정 보존을 위해 nullable — 신규 생성/수정은 폼 검증(@NotBlank)으로 필수화하고,
-- 형식 제약은 애플리케이션(@Pattern)에서 관리한다.
ALTER TABLE managed_users ADD COLUMN phone VARCHAR(30);
