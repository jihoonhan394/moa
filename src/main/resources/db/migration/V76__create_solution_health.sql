-- 솔루션 헬스 체크의 "현재 상태".
--
-- managed_solutions 에 컬럼으로 붙이지 않은 이유가 둘 있다.
--
--  1) 쓰기 주체와 빈도가 정반대다. 설정(managed_solutions)은 관리자가 몇 달에 한 번 고치고,
--     상태는 배치가 분 단위로 덮어쓴다. 한 행에 두면 배치가 updated_at 을 계속 밀어
--     "누가 언제 이 솔루션 설정을 바꿨나"를 더 이상 읽을 수 없다.
--  2) 관리자가 수정 화면을 열어 둔 사이 배치가 같은 행을 쓰면 lost update 가 난다.
--     JPA 는 엔티티 전체를 UPDATE 하므로 한쪽이 상대의 변경을 덮는다.
--
-- 1:1 이라 정규화를 깨는 것이 아니다 — 접근 패턴이 다른 컬럼을 떼어낸 수직 분할이다.
-- 이력(장애가 언제부터 언제까지였나)은 여기 넣지 않는다. 1:N 이므로 별 테이블이고,
-- 알림이 성립하는 데 필요한 것은 "지금 상태"와 "언제 바뀌었나"뿐이다.
CREATE TABLE solution_health (
    solution_id          UUID         PRIMARY KEY,
    tenant_id            UUID         NOT NULL,

    -- UNKNOWN / UP / DOWN. UNKNOWN 은 "아직 못 재 봤다"다 — 설정이 덜 됐거나(포트·자격증명 없음)
    -- 한 번도 점검되지 않은 상태. 장애(DOWN)와 반드시 구별한다. 설정 공백으로 사람을 깨우면
    -- 그 알림은 곧 무시당하고, 진짜 장애도 같이 묻힌다.
    state                VARCHAR(20)  NOT NULL,

    -- 연속 실패 횟수. 1회 실패로 알리지 않는다(네트워크 순간 끊김·재기동 중 한 번은 흔하다).
    consecutive_failures INT          NOT NULL DEFAULT 0,

    -- 마지막으로 실제 점검한 시각. COMMAND 점검의 호출 간격을 여기서 센다.
    checked_at           TIMESTAMP WITH TIME ZONE,

    -- 상태가 마지막으로 '바뀐' 시각. 화면의 "3시간 전부터 DOWN"이 이 값이다.
    changed_at           TIMESTAMP WITH TIME ZONE,

    -- 마지막으로 알림을 보낸 상태. 같은 상태로는 다시 보내지 않는다 —
    -- 이것이 없으면 DOWN 이 지속되는 동안 1분마다 전원에게 알림이 간다.
    alerted_state        VARCHAR(20),

    -- 사람이 읽을 사유("포트 8080 닫힘", "자격증명 미설정"). 화면과 알림 본문에 쓴다.
    detail               VARCHAR(500),

    CONSTRAINT fk_solution_health_solution FOREIGN KEY (solution_id)
        REFERENCES managed_solutions (id) ON DELETE CASCADE
);

-- 실제로 나가는 질의는 둘이다.
--   · 배치: 기관의 솔루션 상태를 한 번에 읽어 온다   tenant_id = ?
--   · 화면: 장애만 추려 보여 준다                    tenant_id = ? AND state = 'DOWN'
-- solution_id 단건 조회는 PK 가 받는다.
CREATE INDEX ix_solution_health_tenant_state ON solution_health (tenant_id, state);
