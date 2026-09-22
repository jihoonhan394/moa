-- 접근요청/승인(JIT) 워크플로우: 표준 권한이 없는 사용자가 특정 서버(자산)에 대해 사유·기간을 적어
-- 요청하면, 소유팀 리더 또는 관리자가 승인해 그 기간 동안만 임시 접속을 허용한다(만료 시 자동 반납).
-- 승인 시 선택적으로 볼트 자격증명(credential)을 함께 내줄 수 있다(접속 시 서버측 주입, 평문 미노출).
-- 모든 조회/변경은 tenant_id로 스코프한다(테넌트 격리 불변식).
CREATE TABLE access_requests (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL REFERENCES tenants (id),
    requester_user_id  UUID NOT NULL,
    asset_id           UUID NOT NULL,
    reason             VARCHAR(1000) NOT NULL,
    requested_start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_end_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    approver_user_id   UUID,
    credential_id      UUID,                                   -- 승인 시 함께 내준 볼트 자격증명(선택)
    review_comment     VARCHAR(1000),
    decided_at         TIMESTAMP WITH TIME ZONE,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_access_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED', 'REVOKED', 'EXPIRED'))
);

-- 요청자 본인 목록 / 승인 대기 목록 / 활성 승인 판정(런타임 게이트)용 인덱스.
CREATE INDEX idx_access_requests_requester ON access_requests (tenant_id, requester_user_id, created_at DESC);
CREATE INDEX idx_access_requests_status    ON access_requests (tenant_id, status, created_at DESC);
CREATE INDEX idx_access_requests_active    ON access_requests (tenant_id, requester_user_id, asset_id, status);
