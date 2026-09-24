-- 회사명 변경(mtcm → moara)에 맞춰 기본 기관의 로그인 코드·이름을 MOARA로 정정한다.
-- 패키지는 com.moara.moa로 이미 리네임했는데 기관 코드만 옛 회사명으로 남아, 로그인 첫 화면과
-- 데모에서 'MTCM'이 그대로 노출됐다.
-- 테넌트 id는 고정(DEFAULT_TENANT_ID)이고 사용자/자산은 id로 귀속되므로 코드/이름만 바뀐다.
-- code='MTCM' 가드로 재적용에 안전(이미 MOARA면 no-op). V17(MOA → MTCM)과 같은 패턴.
UPDATE tenants
   SET code = 'MOARA', name = 'moara', updated_at = CURRENT_TIMESTAMP
 WHERE id = '00000000-0000-0000-0000-000000000001'
   AND code = 'MTCM';
