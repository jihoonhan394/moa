-- 인앱 알림 + 유지보수 담당자/점검 일정.
CREATE TABLE notifications (
  id         UUID PRIMARY KEY,
  tenant_id  UUID NOT NULL REFERENCES tenants (id),
  user_id    UUID NOT NULL REFERENCES managed_users (id) ON DELETE CASCADE,
  title      VARCHAR(200) NOT NULL,
  body       VARCHAR(1000),
  link       VARCHAR(300),
  is_read    BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_notifications_user ON notifications (tenant_id, user_id, is_read, created_at DESC);

CREATE TABLE maintenance_owners (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenants (id),
  target_type VARCHAR(20) NOT NULL,   -- ASSET / SOLUTION
  target_id   UUID NOT NULL,
  user_id     UUID NOT NULL REFERENCES managed_users (id) ON DELETE CASCADE,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_maint_owner UNIQUE (target_type, target_id, user_id),
  CONSTRAINT ck_maint_owner_type CHECK (target_type IN ('ASSET', 'SOLUTION'))
);
CREATE INDEX idx_maint_owners_target ON maintenance_owners (tenant_id, target_type, target_id);
CREATE INDEX idx_maint_owners_user ON maintenance_owners (tenant_id, user_id);

CREATE TABLE maintenance_windows (
  id                 UUID PRIMARY KEY,
  tenant_id          UUID NOT NULL REFERENCES tenants (id),
  target_type        VARCHAR(20) NOT NULL,
  target_id          UUID NOT NULL,
  title              VARCHAR(200) NOT NULL,
  reason             VARCHAR(1000),
  starts_at          TIMESTAMP NOT NULL,
  ends_at            TIMESTAMP,
  created_by_user_id UUID REFERENCES managed_users (id) ON DELETE SET NULL,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_maint_win_type CHECK (target_type IN ('ASSET', 'SOLUTION'))
);
CREATE INDEX idx_maint_windows ON maintenance_windows (tenant_id, target_type, target_id, starts_at);
