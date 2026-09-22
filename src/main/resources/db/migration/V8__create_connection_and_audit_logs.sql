-- 접속 이력(세션 요약 + 이벤트 append-only)과 관리자 행위 감사 로그.
-- 설계 §4에 따라 세 테이블을 V8에서 함께 생성한다.
--   connection_sessions/connection_events: T09에서 도메인/서비스 구현
--   audit_logs: 테이블만 선행 생성, 도메인/서비스는 T11에서 구현
-- connection_events/audit_logs는 감사 기준 테이블이므로 UPDATE/DELETE API를 제공하지 않는다(불변).

CREATE TABLE connection_sessions (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  session_id VARCHAR(100) NOT NULL,
  user_id UUID NOT NULL,
  asset_id UUID NOT NULL,
  protocol VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  client_ip VARCHAR(45),
  failure_code VARCHAR(50),
  started_at TIMESTAMP WITH TIME ZONE,
  ended_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_cs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  CONSTRAINT fk_cs_user FOREIGN KEY (user_id) REFERENCES managed_users (id),
  CONSTRAINT fk_cs_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
  CONSTRAINT uq_cs_session_id UNIQUE (session_id),
  CONSTRAINT ck_cs_protocol CHECK (protocol IN ('SSH', 'RDP')),
  CONSTRAINT ck_cs_status CHECK (status IN ('ATTEMPTING', 'CONNECTED', 'FAILED', 'CLOSED'))
);
CREATE INDEX idx_cs_tenant_created ON connection_sessions (tenant_id, created_at);
CREATE INDEX idx_cs_tenant_user_created ON connection_sessions (tenant_id, user_id, created_at);
CREATE INDEX idx_cs_tenant_asset_created ON connection_sessions (tenant_id, asset_id, created_at);

CREATE TABLE connection_events (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  connection_session_id UUID NOT NULL,
  event_type VARCHAR(20) NOT NULL,
  result VARCHAR(20) NOT NULL,
  message VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_ce_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  CONSTRAINT fk_ce_session FOREIGN KEY (connection_session_id) REFERENCES connection_sessions (id),
  CONSTRAINT ck_ce_event_type CHECK (event_type IN ('ATTEMPT', 'SUCCESS', 'FAILURE', 'CLOSED')),
  CONSTRAINT ck_ce_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);
CREATE INDEX idx_ce_tenant_created ON connection_events (tenant_id, created_at);
CREATE INDEX idx_ce_session_created ON connection_events (connection_session_id, created_at);
CREATE INDEX idx_ce_tenant_type_created ON connection_events (tenant_id, event_type, created_at);

CREATE TABLE audit_logs (
  id UUID PRIMARY KEY,
  action_scope VARCHAR(20) NOT NULL,
  tenant_id UUID,
  target_tenant_id UUID,
  actor_user_id UUID NOT NULL,
  action VARCHAR(100) NOT NULL,
  target_type VARCHAR(50),
  target_id UUID,
  result VARCHAR(20) NOT NULL,
  message VARCHAR(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_al_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  CONSTRAINT fk_al_actor FOREIGN KEY (actor_user_id) REFERENCES managed_users (id),
  CONSTRAINT ck_al_scope CHECK (action_scope IN ('GLOBAL', 'TENANT')),
  CONSTRAINT ck_al_result CHECK (result IN ('SUCCESS', 'FAILURE'))
);
CREATE INDEX idx_al_tenant_created ON audit_logs (tenant_id, created_at);
CREATE INDEX idx_al_scope_created ON audit_logs (action_scope, created_at);
CREATE INDEX idx_al_actor_created ON audit_logs (actor_user_id, created_at);
