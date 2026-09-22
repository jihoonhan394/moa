-- 공유자산 예약 도메인(제품비전 §1: 차량·회의실·좌석 = 예약). 자산 관리자가 공유자산을 등록하고
-- 일반 사용자가 시간대로 예약한다(중복 방지). 퇴사 시 향후 예약은 취소된다.
CREATE TABLE shared_resources (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenants (id),
  name        VARCHAR(100) NOT NULL,
  type        VARCHAR(20) NOT NULL,
  location    VARCHAR(200),
  capacity    INTEGER,
  status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  description VARCHAR(500),
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_shared_resources_tenant_name UNIQUE (tenant_id, name),
  CONSTRAINT ck_shared_resources_type CHECK (type IN ('VEHICLE', 'ROOM', 'SEAT', 'OTHER')),
  CONSTRAINT ck_shared_resources_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE reservations (
  id          UUID PRIMARY KEY,
  tenant_id   UUID NOT NULL REFERENCES tenants (id),
  resource_id UUID NOT NULL REFERENCES shared_resources (id) ON DELETE CASCADE,
  user_id     UUID NOT NULL REFERENCES managed_users (id) ON DELETE CASCADE,
  starts_at   TIMESTAMP NOT NULL,
  ends_at     TIMESTAMP NOT NULL,
  purpose     VARCHAR(300),
  status      VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_reservations_status CHECK (status IN ('BOOKED', 'CANCELLED'))
);

CREATE INDEX idx_reservations_resource_time ON reservations (resource_id, starts_at);
CREATE INDEX idx_reservations_user ON reservations (tenant_id, user_id);

-- 데모(기본) 기관에 예약 기능을 켜 둔다.
INSERT INTO tenant_features (tenant_id, feature)
VALUES ('00000000-0000-0000-0000-000000000001', 'RESERVATION')
ON CONFLICT DO NOTHING;
