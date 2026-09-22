-- SSL 자동감지 트래커 강화: 재프로브용 호스트/포트와 인증서 상세(발급자·유효기간·SAN 요약)를 저장한다.
-- 모두 nullable(기존 수동 항목·과거 SSL 항목엔 값이 없음). 일 배치가 probe_host/probe_port로 재프로브해
-- 만료일과 상세를 갱신한다. 발급·설치는 하지 않는다(읽기 전용 조회 목적).
ALTER TABLE resource_trackers ADD COLUMN probe_host VARCHAR(255);
ALTER TABLE resource_trackers ADD COLUMN probe_port INT;
ALTER TABLE resource_trackers ADD COLUMN detail     VARCHAR(500);
