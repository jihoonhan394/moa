-- T21-1a: 자격증명 볼트. 솔루션 계정(비번/키)을 봉투암호화로 저장한다.
-- 평문 컬럼 없음: secret_ciphertext = AES-256-GCM(DEK, 비밀), dek_wrapped = AES-256-GCM(KEK, DEK).
-- KEK는 DB 밖(env/KMS). key_version은 KEK 로테이션 대비.
CREATE TABLE credentials (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL REFERENCES tenants (id),
    name              VARCHAR(100) NOT NULL,
    type              VARCHAR(20) NOT NULL,
    username          VARCHAR(255) NOT NULL,
    secret_ciphertext TEXT NOT NULL,
    dek_wrapped       TEXT NOT NULL,
    key_version       INT NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_credentials_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_credentials_type CHECK (type IN ('PASSWORD', 'SSH_KEY'))
);
