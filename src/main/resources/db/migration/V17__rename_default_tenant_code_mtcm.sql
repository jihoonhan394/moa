-- T05: 데모/샘플 기본 테넌트의 로그인 코드를 MOA → MTCM으로 변경.
-- 테넌트 id는 고정(DEFAULT_TENANT_ID)이며 사용자/자산은 id로 귀속되므로 코드/이름만 바뀐다.
-- code='MOA' 가드로 재적용에 안전(이미 MTCM이면 no-op).
UPDATE tenants
   SET code = 'MTCM', name = 'MTCM', updated_at = CURRENT_TIMESTAMP
 WHERE id = '00000000-0000-0000-0000-000000000001'
   AND code = 'MOA';
