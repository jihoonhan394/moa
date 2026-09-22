-- 솔루션 기동 순서(오케스트레이션). 여러 솔루션(서로 다른 서버 가능)을 순서대로 시작(오름차순)하고
-- 역순으로 중지한다. 예: 서버A Oracle DB → 서버A 리스너 → 서버B WAS. 각 단계에 시작 후 대기(초)를 둘 수 있다.
CREATE TABLE solution_sequences (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenants (id),
  name        VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_solution_sequences_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE solution_sequence_steps (
  id           UUID PRIMARY KEY,
  tenant_id    UUID NOT NULL REFERENCES tenants (id),
  sequence_id  UUID NOT NULL REFERENCES solution_sequences (id) ON DELETE CASCADE,
  solution_id  UUID NOT NULL REFERENCES managed_solutions (id) ON DELETE CASCADE,
  position     INTEGER NOT NULL,
  wait_seconds INTEGER NOT NULL DEFAULT 0,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_solution_sequence_steps_seq ON solution_sequence_steps (sequence_id, position);
