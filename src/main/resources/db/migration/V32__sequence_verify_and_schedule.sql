-- 기동 순서 심화: (1) 단계별 동작(시작/중지/재시작) — 페일오버처럼 한 시퀀스에 섞어 넣을 수 있다,
-- (2) 단계별 시작 후 상태 확인(STATUS 게이팅), (3) cron 스케줄로 정/역방향 자동 실행.
ALTER TABLE solution_sequence_steps ADD COLUMN action VARCHAR(20) NOT NULL DEFAULT 'START';
ALTER TABLE solution_sequence_steps ADD COLUMN verify_after_start BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE solution_sequence_steps
  ADD CONSTRAINT ck_sequence_steps_action CHECK (action IN ('START', 'STOP', 'RESTART', 'STATUS'));

-- 정방향(각 단계 동작 그대로) / 역방향(반대 동작을 역순으로) 자동 실행 cron.
ALTER TABLE solution_sequences ADD COLUMN forward_cron VARCHAR(100);
ALTER TABLE solution_sequences ADD COLUMN reverse_cron VARCHAR(100);
-- 스케줄 중복 발화 방지용: 마지막으로 발화한 예정 시각 키(ISO local).
ALTER TABLE solution_sequences ADD COLUMN last_forward_fired VARCHAR(30);
ALTER TABLE solution_sequences ADD COLUMN last_reverse_fired VARCHAR(30);
