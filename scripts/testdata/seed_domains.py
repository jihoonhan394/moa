"""2차 시딩 — 자산·서버 말고 나머지 도메인을 실제처럼 채운다.

그룹 · 소모품 · 공유자산 · 예약 · 공지 · 자격증명 · 위키 · 만료 임박 라이선스
"""
import datetime as dt
import random
import re
import sys

sys.path.insert(0, str(__import__('pathlib').Path(__file__).parent))
import seed_tenant as seed

random.seed(20261003)
TODAY = dt.date.today()


def main():
    client, status, _ = seed.tenant_login(seed.TA_EMAIL, seed.TA_PASS)
    print('로그인:', status)

    _, users_html = client.get('/users')
    user_ids = list(dict.fromkeys(re.findall(r'/users/([0-9a-f-]{36})/edit', users_html)))
    print('사용자:', len(user_ids))

    # ── 조직(그룹) ────────────────────────────────────────────────
    groups = ['경영지원팀', '인프라관리실', '개발1팀', '개발2팀', '영업팀', '품질보증팀']
    made = 0
    for name in groups:
        token = client.csrf('/groups')
        status, location, _ = client.post('/groups', [
            ('name', name), ('description', f'{name} 입니다'), ('status', 'ACTIVE'), ('_csrf', token)])
        if seed.ok(status, location):
            made += 1
    print('그룹:', made)

    _, groups_html = client.get('/groups')
    group_ids = list(dict.fromkeys(re.findall(r'/groups/([0-9a-f-]{36})', groups_html)))
    members = 0
    for n, user in enumerate(user_ids):
        if not group_ids:
            break
        group = group_ids[n % len(group_ids)]
        token = client.csrf(f'/groups/{group}')
        status, location, _ = client.post(f'/groups/{group}/members', [
            ('userId', user), ('_csrf', token)])
        if seed.ok(status, location):
            members += 1
    print('그룹 멤버:', members)

    # ── 소모품 ────────────────────────────────────────────────────
    supplies = [
        ('A4 복사용지', '사무용품', '박스', 30), ('토너 HP CF259A', '소모품', '개', 90),
        ('토너 삼성 MLT-D203', '소모품', '개', 90), ('볼펜(흑)', '사무용품', '다스', 60),
        ('포스트잇', '사무용품', '개', 45), ('건전지 AA', '사무용품', '팩', 120),
        ('커피 원두', '탕비실', 'kg', 14), ('종이컵', '탕비실', '줄', 21),
        ('생수 2L', '탕비실', '박스', 7), ('마우스', '전산소모품', '개', 365),
        ('키보드', '전산소모품', '개', 365), ('HDMI 케이블', '전산소모품', '개', None),
    ]
    made = 0
    for name, category, unit, cycle in supplies:
        token = client.csrf('/consumables')
        status, location, _ = client.post('/consumables', [
            ('name', name), ('category', category), ('unit', unit),
            ('cycleDays', cycle if cycle else ''),
            ('note', random.choice(['', '', '2층 창고', '총무팀 보관'])),
            ('_csrf', token)])
        if seed.ok(status, location):
            made += 1
    print('소모품:', made)

    _, cons_html = client.get('/consumables')
    cons_ids = list(dict.fromkeys(re.findall(r'/consumables/([0-9a-f-]{36})', cons_html)))
    orders = 0
    for n, cid in enumerate(cons_ids):
        for back in (120, 60, 20):
            token = client.csrf(f'/consumables/{cid}')
            ordered = TODAY - dt.timedelta(days=back + n)
            status, location, _ = client.post(f'/consumables/{cid}/orders', [
                ('orderedOn', ordered.isoformat()),
                ('quantity', random.randint(1, 12)),
                ('note', random.choice(['', '정기 구매', '급히 소진'])),
                ('_csrf', token)])
            if seed.ok(status, location):
                orders += 1
    print('소모품 주문 이력:', orders)

    # ── 공유자산 + 예약 ────────────────────────────────────────────
    resources = [
        ('대회의실', '회의실', '본사 3층', 20), ('소회의실 A', '회의실', '본사 3층', 6),
        ('소회의실 B', '회의실', '본사 4층', 6), ('교육장', '회의실', '본사 5층', 40),
        ('법인차량 1호(쏘렌토)', '차량', '지하 주차장', 5),
        ('법인차량 2호(아이오닉)', '차량', '지하 주차장', 5),
        ('빔프로젝터', '장비', '총무팀', 1), ('노트북(대여용)', '장비', '전산실', 1),
    ]
    made = 0
    for name, category, location_text, capacity in resources:
        token = client.csrf('/shared-resources')
        status, location, _ = client.post('/shared-resources', [
            ('name', name), ('category', category), ('location', location_text),
            ('capacity', capacity), ('status', 'ACTIVE'),
            ('description', f'{category} · 사전 예약제'), ('_csrf', token)])
        if seed.ok(status, location):
            made += 1
    print('공유자산:', made)

    _, res_html = client.get('/shared-resources')
    resource_ids = list(dict.fromkeys(re.findall(r'/shared-resources/([0-9a-f-]{36})', res_html)))
    booked = 0
    for n in range(24):
        if not resource_ids:
            break
        start = dt.datetime.combine(TODAY + dt.timedelta(days=n % 10), dt.time(9 + (n % 8), 0))
        end = start + dt.timedelta(hours=1 + (n % 3))
        token = client.csrf('/reservations')
        status, location, _ = client.post('/reservations', [
            ('resourceId', resource_ids[n % len(resource_ids)]),
            ('startsAt', start.strftime('%Y-%m-%dT%H:%M')),
            ('endsAt', end.strftime('%Y-%m-%dT%H:%M')),
            ('purpose', random.choice(['주간 회의', '고객 미팅', '교육', '출장', '면접', '장비 대여'])),
            ('_csrf', token)])
        if seed.ok(status, location):
            booked += 1
    print('예약:', booked)

    # ── 공지 ──────────────────────────────────────────────────────
    notices = [
        ('[필독] 정보보안 교육 이수 안내', '전 직원은 이달 말까지 교육을 이수해 주세요.', True),
        ('추석 연휴 근무 안내', '연휴 기간 당직 근무표를 공유합니다.', True),
        ('사내 Wi-Fi 비밀번호 변경', '보안 정책에 따라 분기마다 변경됩니다.', False),
        ('노트북 반납 요청', '교체 대상 노트북은 총무팀에 반납해 주세요.', False),
        ('회의실 예약 규칙 변경', '2시간 이상 예약은 사전 승인이 필요합니다.', False),
        ('연말 자산 실사 일정', '12월 둘째 주에 실사를 진행합니다.', False),
    ]
    made = 0
    for title, body, pinned in notices:
        token = client.csrf('/notices')
        fields = [('title', title), ('body', body), ('_pinned', 'on'), ('_emailToUsers', 'on'), ('_csrf', token)]
        if pinned:
            fields.append(('pinned', 'true'))
        status, location, _ = client.post('/notices', fields)
        if seed.ok(status, location):
            made += 1
    print('공지:', made)

    # ── 자격증명(볼트) ─────────────────────────────────────────────
    creds = [
        ('srv-01 root', 'PASSWORD', 'root'), ('srv-02 root', 'PASSWORD', 'root'),
        ('PostgreSQL 운영', 'PASSWORD', 'moa_app'), ('GitLab 배포키', 'SSH_KEY', 'deploy'),
        ('Jenkins 관리자', 'PASSWORD', 'admin'), ('NAS 관리자', 'PASSWORD', 'nasadmin'),
        ('방화벽 콘솔', 'PASSWORD', 'fwadmin'), ('백업 서버', 'SSH_KEY', 'backup'),
        ('그룹웨어 DB', 'PASSWORD', 'gwdb'), ('모니터링 대시보드', 'PASSWORD', 'grafana'),
    ]
    made = 0
    for name, ctype, username in creds:
        token = client.csrf('/credentials')
        status, location, _ = client.post('/credentials', [
            ('name', name), ('type', ctype), ('username', username),
            ('url', f'https://{username}.hanbit.local'),
            ('secret', 'seed-only-not-a-real-secret-' + str(random.randint(10000, 99999))),
            ('_csrf', token)])
        if seed.ok(status, location):
            made += 1
    print('자격증명:', made)

    # ── 위키 ──────────────────────────────────────────────────────
    spaces = ['운영 매뉴얼', '장애 대응', '신입 온보딩', '보안 정책']
    made = 0
    for name in spaces:
        token = client.csrf('/wiki')
        status, location, _ = client.post('/wiki/spaces', [
            ('name', name), ('description', f'{name} 문서 공간'), ('_csrf', token)])
        if seed.ok(status, location):
            made += 1
    print('위키 공간:', made)

    _, wiki_html = client.get('/wiki')
    space_ids = list(dict.fromkeys(re.findall(r'/wiki/spaces/([0-9a-f-]{36})', wiki_html)))
    pages = [
        ('서버 재기동 절차', '1. 영향도 확인\n2. 공지\n3. 순서대로 재기동\n4. 상태 확인'),
        ('백업 복구 절차', '야간 전체 백업, 보관 30일. 복구는 스테이징에서 먼저 검증한다.'),
        ('장애 등급 기준', 'P1 전사 중단 / P2 부분 장애 / P3 단일 사용자'),
        ('신입 계정 발급', '입사 당일 계정 생성 → 교육 이수 → 권한 부여'),
        ('노트북 지급 기준', '개발 16GB 이상, 사무 8GB. 3년 교체 주기.'),
        ('VPN 접속 방법', '사내망 외부 접속은 VPN 필수. 2차 인증 필요.'),
        ('비밀번호 정책', '12자 이상, 분기 변경, 재사용 금지'),
        ('반출입 절차', '자산 반출은 사전 승인 후 반출증을 발급한다.'),
        ('정기 점검 체크리스트', '디스크 사용률 / 백업 성공 여부 / 인증서 만료일'),
        ('인증서 갱신', '만료 30일 전 알림. 갱신 후 서비스 재기동 필요.'),
        ('협력업체 연락망', '유지보수 업체별 담당자와 연락처를 정리한다.'),
        ('재고 실사 방법', 'QR 스캔으로 훑고, 미확인 목록을 대조한다.'),
    ]
    made = 0
    for n, (title, content) in enumerate(pages):
        if not space_ids:
            break
        space = space_ids[n % len(space_ids)]
        token = client.csrf('/wiki')
        status, location, _ = client.post(f'/wiki/spaces/{space}/pages', [
            ('title', title), ('content', content), ('_csrf', token)])
        if seed.ok(status, location):
            made += 1
    print('위키 문서:', made)

    # ── 만료가 걸린 SW 라이선스 ────────────────────────────────────
    _, inv_html = client.get('/inventory')
    paths = [o for o in re.findall(r'<option value="([^"]+)"', inv_html) if ' / ' in o]
    sw_paths = [p for p in paths if p.startswith('SW')] or paths
    licenses = [
        ('MS Office 365 (영업팀)', -40), ('MS Office 365 (개발팀)', -5),
        ('Adobe Creative Cloud', 3), ('AutoCAD', 12), ('한컴오피스', 25),
        ('Jira 라이선스', 60), ('Confluence 라이선스', 120), ('백신 기업용', 200),
        ('SSL 인증서 (대표 도메인)', 18), ('SSL 인증서 (API)', 95),
    ]
    made = 0
    for n, (name, offset) in enumerate(licenses):
        token = client.csrf('/inventory')
        status, location, _ = client.post('/inventory', [
            ('name', f'{name} #{n + 1:02d}'),
            ('category', sw_paths[n % len(sw_paths)] if sw_paths else ''),
            ('expiresAt', (TODAY + dt.timedelta(days=offset)).isoformat()),
            ('quantity', random.choice([1, 1, 5, 10])),
            ('note', '자동 갱신 아님'),
            ('_csrf', token)])
        if seed.ok(status, location):
            made += 1
    print('만료 추적 라이선스:', made)

    print('\n2차 시딩 완료')


if __name__ == '__main__':
    main()
