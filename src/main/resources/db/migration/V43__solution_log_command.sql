-- 솔루션 로그 수집 명령(온디맨드 분석용). 예: tail -n 1000 /var/log/app/app.log 2>&1
-- 비어 있으면 로그 분석 비활성. 원격 실행은 기존 제어와 동일 채널(SSH/WinRM + 볼트 자격증명).
ALTER TABLE managed_solutions ADD COLUMN log_command VARCHAR(1000);
