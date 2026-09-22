-- 솔루션 사용자 배정: 일반 사용자(non-IT)가 배정받은 솔루션을 start/stop 제어할 수 있게 한다.
-- 등록·유지보수는 자원 관리자, 운영(제어)은 배정받은 사용자 — 원래 기획의 계층.
CREATE TABLE solution_user_assignments (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenants (id),
  solution_id UUID NOT NULL REFERENCES managed_solutions (id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES managed_users (id) ON DELETE CASCADE,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_solution_user UNIQUE (solution_id, user_id)
);
CREATE INDEX idx_solution_user_user ON solution_user_assignments (tenant_id, user_id);
