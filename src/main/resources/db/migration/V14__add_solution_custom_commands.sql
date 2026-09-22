-- T21-1c: CUSTOM_COMMAND 유형(명령 직접 지정: Oracle 스크립트/임의 프로세스/root 불필요 데모 등) 지원.
ALTER TABLE managed_solutions ADD COLUMN start_command  VARCHAR(1000);
ALTER TABLE managed_solutions ADD COLUMN stop_command   VARCHAR(1000);
ALTER TABLE managed_solutions ADD COLUMN status_command VARCHAR(1000);

ALTER TABLE managed_solutions DROP CONSTRAINT ck_managed_solutions_type;
ALTER TABLE managed_solutions ADD CONSTRAINT ck_managed_solutions_type
    CHECK (type IN ('WINDOWS_SERVICE', 'WINDOWS_EXE', 'LINUX_DAEMON', 'DOCKER_CONTAINER', 'CUSTOM_COMMAND'));
