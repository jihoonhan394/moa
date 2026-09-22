-- T03: 아이디 유일성을 "전역"에서 "기관(테넌트)별"로 전환한다(두레이식 진입).
-- 같은 아이디를 서로 다른 기관에서 각각 사용할 수 있게 하되, 한 기관 안에서는 유일하도록 강제한다.

-- 1) 전역 UNIQUE 제약 해제. Postgres는 컬럼 인라인 UNIQUE를 <table>_<col>_key로 자동 명명한다.
--    (H2 등 다른 엔진은 자동 명명 규칙이 달라 매칭되지 않을 수 있으므로 IF EXISTS로 안전 처리.
--     매칭 실패 시 no-op이며, 테스트는 기관별 중복 아이디를 넣지 않아 영향이 없다.)
ALTER TABLE managed_users DROP CONSTRAINT IF EXISTS managed_users_username_key;
ALTER TABLE managed_users DROP CONSTRAINT IF EXISTS managed_users_email_key;

-- 2) 기관별 유일 복합 UNIQUE. 표준 SQL UNIQUE는 NULL을 서로 다른 값으로 취급하므로
--    tenant_id가 NULL인 플랫폼 관리자(SYSTEM_ADMIN)는 이 제약의 대상이 되지 않는다
--    (플랫폼 관리자 아이디 유일성은 프로비저닝/앱 계층에서 보장). email은 선택값(NULL 허용).
ALTER TABLE managed_users
  ADD CONSTRAINT ux_managed_users_tenant_username UNIQUE (tenant_id, username);
ALTER TABLE managed_users
  ADD CONSTRAINT ux_managed_users_tenant_email UNIQUE (tenant_id, email);
