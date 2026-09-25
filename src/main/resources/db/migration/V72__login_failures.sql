-- 로그인 실패를 남기고, 반복되면 잠근다.
--
-- 지금까지 /login에는 비밀번호를 무한히 넣어 볼 수 있었고 시도했다는 사실조차 남지 않았다.
-- 성공(USER_LOGIN)만 기록됐다. 자격증명 볼트를 들고 있는 제품에서 현관문만 무방비였던 셈이다.

-- 1) 감사 로그의 행위자를 선택 항목으로.
--
-- 실패한 로그인의 대부분은 "없는 계정"이다 — 사용자명을 훑는 공격이 정확히 그 형태다.
-- actor_user_id가 NOT NULL이면 그 시도는 남길 자리가 없어, 가장 보고 싶은 공격이 감사에서
-- 통째로 빠진다. FK는 그대로 둔다(NULL은 FK를 위반하지 않는다) — 값이 있으면 여전히 실재하는
-- 사용자여야 한다.
ALTER TABLE audit_logs ALTER COLUMN actor_user_id DROP NOT NULL;

-- 2) 잠금 판단용 실패 기록.
--
-- 감사 로그와 나누는 이유는 수명이 다르기 때문이다. 감사는 영구 기록이고 지우지 않는다.
-- 이 표는 "최근 15분에 몇 번 틀렸나"만 답하면 되는 운영 상태라, 창이 지나면 지워도 된다.
-- 성공하면 그 계정의 행을 지우므로 평소에는 거의 비어 있다.
CREATE TABLE login_failures (
    id           UUID PRIMARY KEY,
    -- 기관 범위. 플랫폼(SYSTEM_ADMIN) 로그인은 소속 기관이 없어 전부 0인 UUID를 쓴다 —
    -- NULL로 두면 (tenant_id = ?) 조회가 영영 일치하지 않아 플랫폼 계정만 잠기지 않는다.
    tenant_id    UUID         NOT NULL,
    username     VARCHAR(255) NOT NULL,
    client_ip    VARCHAR(64)  NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 계정 단위 집계.
CREATE INDEX idx_login_failures_account ON login_failures (tenant_id, username, attempted_at);
-- 출발지 단위 집계(계정을 바꿔 가며 훑는 공격은 계정 카운터에 안 걸린다).
CREATE INDEX idx_login_failures_ip ON login_failures (client_ip, attempted_at);
