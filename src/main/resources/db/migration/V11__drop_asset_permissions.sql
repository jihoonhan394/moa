-- T20: 옛 직접 권한 테이블 제거. 접근 판정은 묶음 권한 모델(permissions/permission_entries/*_assignments, T17~T18)로 대체됨.
-- V7에서 생성한 asset_permissions는 더 이상 사용되지 않는다.
DROP TABLE IF EXISTS asset_permissions;
