-- 솔루션 제어 채널(SSH/WinRM)과 선택적 제어 포트.
-- 기존 행은 SSH로 간주(default), 포트는 미지정 시 서비스에서 프로토콜별 기본값을 사용한다.
ALTER TABLE managed_solutions ADD COLUMN control_protocol VARCHAR(20) NOT NULL DEFAULT 'SSH';
ALTER TABLE managed_solutions ADD COLUMN control_port INTEGER;
ALTER TABLE managed_solutions
    ADD CONSTRAINT chk_solution_control_protocol CHECK (control_protocol IN ('SSH', 'WINRM'));
