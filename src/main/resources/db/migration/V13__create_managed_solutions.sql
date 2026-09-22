-- T21-1b: 제어 대상 솔루션(단일 서버 포함). 서버 자산에 속하고, 제어 자격증명(볼트)을 참조한다.
-- 이중화 그룹/순서는 Phase 2에서 컬럼 추가 예정(지금은 단일 서버 제어).
CREATE TABLE managed_solutions (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES tenants (id),
    asset_id            UUID NOT NULL REFERENCES assets (id),
    name                VARCHAR(100) NOT NULL,
    type                VARCHAR(20) NOT NULL,          -- WINDOWS_SERVICE|WINDOWS_EXE|LINUX_DAEMON|DOCKER_CONTAINER
    identifier          VARCHAR(500) NOT NULL,         -- 서비스명 / exe 경로 / systemd 유닛명
    credential_id       UUID REFERENCES credentials (id),  -- 제어 로그인/run-as (nullable)
    health_check_type   VARCHAR(20) NOT NULL DEFAULT 'NONE',  -- NONE|TCP_PORT|COMMAND
    health_check_target VARCHAR(500),                  -- 포트 또는 명령
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_managed_solutions UNIQUE (tenant_id, asset_id, name),
    CONSTRAINT ck_managed_solutions_type CHECK (type IN ('WINDOWS_SERVICE', 'WINDOWS_EXE', 'LINUX_DAEMON', 'DOCKER_CONTAINER')),
    CONSTRAINT ck_managed_solutions_health CHECK (health_check_type IN ('NONE', 'TCP_PORT', 'COMMAND')),
    CONSTRAINT ck_managed_solutions_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE INDEX idx_managed_solutions_asset ON managed_solutions (tenant_id, asset_id);
