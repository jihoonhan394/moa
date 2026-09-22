-- 외부 연동 도구 카탈로그와 자산↔도구 연결. 1차 실제 접속 도구는 Guacamole 하나.
-- 비밀정보(관리자 비번/API 키/서명키/토큰) 저장 금지 — secret은 환경변수, DB엔 식별자/주소만.
-- 1차 연동은 접속 시점 자격증명 입력 + env 기반이라 이 테이블은 스키마/확장 여지 확보 목적으로 선행 생성한다.
CREATE TABLE integration_tools (
  id UUID PRIMARY KEY,
  tool_type VARCHAR(30) NOT NULL,
  name VARCHAR(100) NOT NULL,
  base_url VARCHAR(2048) NOT NULL,
  status VARCHAR(20) NOT NULL,
  auth_type VARCHAR(20) NOT NULL,
  license_name VARCHAR(50),
  license_review_status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_it_tool_type CHECK (tool_type IN
    ('GUACAMOLE', 'RUNDECK', 'PROMETHEUS', 'GRAFANA', 'COCKPIT', 'PACEMAKER', 'KEEPALIVED', 'HA_VENDOR', 'OTHER')),
  CONSTRAINT ck_it_status CHECK (status IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT ck_it_auth_type CHECK (auth_type IN ('NONE', 'BASIC', 'OIDC', 'SIGNED_URL', 'PROXY')),
  CONSTRAINT ck_it_license_review CHECK (license_review_status IN ('NOT_REVIEWED', 'REVIEWED', 'NEEDS_LEGAL_REVIEW'))
);
CREATE INDEX idx_it_type_status ON integration_tools (tool_type, status);

CREATE TABLE asset_integration_links (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  asset_id UUID NOT NULL,
  integration_tool_id UUID NOT NULL,
  protocol VARCHAR(20) NOT NULL,
  external_resource_id VARCHAR(255),
  launch_path VARCHAR(2048),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_ail_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
  CONSTRAINT fk_ail_asset FOREIGN KEY (asset_id) REFERENCES assets (id),
  CONSTRAINT fk_ail_tool FOREIGN KEY (integration_tool_id) REFERENCES integration_tools (id),
  CONSTRAINT uq_ail UNIQUE (tenant_id, asset_id, integration_tool_id, protocol),
  CONSTRAINT ck_ail_protocol CHECK (protocol IN
    ('SSH', 'RDP', 'HTTP', 'HTTPS', 'WEB', 'METRICS', 'CONSOLE', 'RUNBOOK', 'HA')),
  CONSTRAINT ck_ail_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
CREATE INDEX idx_ail_tenant_asset ON asset_integration_links (tenant_id, asset_id);
CREATE INDEX idx_ail_tenant_tool ON asset_integration_links (tenant_id, integration_tool_id);
