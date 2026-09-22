-- 자원별 만기·점검 항목(공용: 인벤토리 실물/SW + 서버/접속 자산). 라벨+예정일, 선택적 반복주기.
--  · 1회성(recur NULL): 인증서·도메인·보험·검사 만기 등
--  · 반복(recur=일수): 필터 교체·정기점검 등(완료 시 다음 일정으로 롤)
--  · source: MANUAL(수동 기록) / SSL_PROBE(서버 TLS 자동 감지로 채움)
-- 모두 만료 통합 대시보드/알림에 자동 반영. 금액·계약은 저장하지 않는다(운영 만기까지, 재무는 ERP).
CREATE TABLE resource_trackers (
  id               UUID PRIMARY KEY,
  tenant_id        UUID NOT NULL REFERENCES tenants (id),
  target_type      VARCHAR(20) NOT NULL,   -- INVENTORY / ASSET
  target_id        UUID NOT NULL,
  label            VARCHAR(100) NOT NULL,
  due_on           DATE NOT NULL,
  recur_every_days INT,
  last_done_on     DATE,
  source           VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT ck_tracker_target CHECK (target_type IN ('INVENTORY', 'ASSET'))
);
CREATE INDEX idx_resource_trackers ON resource_trackers (tenant_id, target_type, target_id);
