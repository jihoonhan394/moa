-- 팀 내 대직(임시 부재 권한). 부서장이 자기 팀 안에서 "부재 팀원(absent)의 일을 대직자(deputy)가 대신"으로
-- 기간제 지정한다. 그 창(starts_on~ends_on) 동안 대직자의 유효 접근 = 본인 ∪ 부재자(운영 접근: 서버 연결·솔루션 제어).
-- 날짜로 자동 만료(별도 배치 없음, 계산형). 역할 복사 없음 → 종료 시 조치 없이 자동 원복.
CREATE TABLE deputy_delegations (
  id                 UUID PRIMARY KEY,
  tenant_id          UUID NOT NULL REFERENCES tenants (id),
  absent_user_id     UUID NOT NULL REFERENCES managed_users (id),
  deputy_user_id     UUID NOT NULL REFERENCES managed_users (id),
  starts_on          DATE NOT NULL,
  ends_on            DATE NOT NULL,
  created_by_user_id UUID,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_deputy_by_deputy ON deputy_delegations (tenant_id, deputy_user_id);
CREATE INDEX idx_deputy_by_tenant ON deputy_delegations (tenant_id);
