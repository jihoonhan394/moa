CREATE TABLE assets (
  id UUID PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  asset_type VARCHAR(20) NOT NULL,
  protocol VARCHAR(20) NOT NULL,
  host VARCHAR(255),
  port INTEGER,
  url VARCHAR(2048),
  os_type VARCHAR(20),
  description VARCHAR(1000),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_assets_type CHECK (asset_type IN ('SERVER', 'WEBSITE')),
  CONSTRAINT ck_assets_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_assets_type_status ON assets (asset_type, status);
