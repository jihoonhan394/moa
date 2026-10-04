"""모바일 화면(/m/**) 전 과정 + 이상한 값 넣어 보기.

    python scripts/testdata/test_mobile_flow.py

앱이 웹뷰로 타는 경로와 같다. 화면 캡처 없이 빠르게 돌려 회귀를 잡는 용도다.
선행: run_all.py 로 데이터가 채워져 있어야 한다.
"""
import pathlib
import re
import sys
import urllib.parse

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import seed_tenant as seed

PASS, FAIL = [], []


def check(label, condition, detail=''):
    (PASS if condition else FAIL).append(label)
    mark = 'PASS' if condition else 'FAIL'
    print(f'  [{mark}] {label}' + (f' — {detail}' if detail else ''))


def text_of(html):
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', html))


def free_code(client):
    _, html = client.get('/inventory/tags')
    codes = re.findall(r'MOA-[0-9A-Z]{4}-[0-9A-Z]{4}', html)
    return codes[0] if codes else None


def main():
    client, status, location = seed.tenant_login(seed.TA_EMAIL, seed.TA_PASS)
    # ⚠️ 302 만 보면 안 된다. 로그인 실패도 302(/login?error) 다.
    if 'dashboard' not in (location or ''):
        print(f'로그인 실패({status} {location}) — run_all.py 를 먼저 돌린다')
        sys.exit(1)

    print('\n━━━ 화면이 뜨는가 ━━━')
    for path, label in [
        ('/m', '홈'), ('/m/scan', '스캔'), ('/m/approvals', '승인'),
        ('/m/me', '내 것'), ('/m/me/requests', '내 요청'), ('/m/notices', '공지'),
        ('/m/notifications', '알림'), ('/m/solutions', '솔루션'),
    ]:
        code, _ = client.get(path)
        check(f'{label} ({path})', code == 200, str(code))

    print('\n━━━ QR 자산 등록 → 조회 ━━━')
    code = free_code(client)
    check('빈 관리번호 확보', code is not None, code or '없음')
    if not code:
        summary()

    status_code, html = client.get(f'/m/t/{code}')
    check('빈 번호를 찍으면 등록 화면', status_code == 200 and '자산 등록' in text_of(html))

    name = f'현장등록-{code[-4:]}'
    _, inv = client.get('/inventory')
    category = next((o for o in re.findall(r'<option value="([^"]+)"', inv) if ' / ' in o), '')
    token = client.csrf(f'/m/t/{code}')
    s, loc, body = client.post(f'/m/t/{code}', [
        ('name', name), ('category', category), ('_csrf', token)])
    check('등록 제출', seed.ok(s, loc), f'{s} {loc}')

    s2, detail = client.get(f'/m/t/{code}')
    check('같은 번호가 이제 조회 화면', s2 == 200 and name in text_of(detail), name)
    check('조회 화면에 관리번호가 보인다', code in text_of(detail))

    print('\n━━━ 알림 ━━━')
    # 자산을 사람에게 배정하면 그 사람에게 알림이 간다.
    _, users_html = client.get('/users')
    me_row = next((c for c in re.split(r'(?=<div class="table-row)', users_html)
                   if seed.TA_EMAIL in c), '')
    me_id = (re.findall(r'([0-9a-f]{8}-[0-9a-f-]{27})', me_row) or [None])[0]
    _, inv2 = client.get('/inventory')
    item_id = next((m for m in re.findall(r'/inventory/([0-9a-f-]{36})', inv2)), None)
    before = len(re.findall(r'class="m-row"', client.get('/m/notifications')[1]))
    if me_id and item_id:
        token = client.csrf('/inventory')
        client.post(f'/inventory/{item_id}/assign', [('userId', me_id), ('_csrf', token)])
    after_html = client.get('/m/notifications')[1]
    after = len(re.findall(r'class="m-row"', after_html))
    check('자산 배정이 알림을 만든다', after > before, f'{before} → {after}')
    check('알림 화면에 제목이 보인다', '자산' in text_of(after_html))

    token = client.csrf('/m/notifications')
    s, loc, _ = client.post('/m/notifications/read-all', [('_csrf', token)])
    check('모두 읽음', seed.ok(s, loc), f'{s} {loc}')
    check('홈에 안 읽음이 사라진다', '안 읽음' not in text_of(client.get('/m')[1]))

    print('\n━━━ 이상한 값 ━━━')
    probes = [
        ('빈 코드', '/m/t/'),
        ('형식 오류', '/m/t/ABCD'),
        ('검사문자 불일치', '/m/t/MOA-0000-0001'),
        ('아주 긴 코드', '/m/t/' + 'A' * 500),
        ('경로 탈출', '/m/t/..%2F..%2Fadmin'),
        ('스크립트 삽입', '/m/t/' + urllib.parse.quote('<script>alert(1)</script>')),
        ('널 바이트', '/m/t/MOA%00-0000-0000'),
        ('한글', '/m/t/' + urllib.parse.quote('관리번호')),
    ]
    for label, path in probes:
        status_code, body = client.get(path)
        check(f'{label} → 500 이 아니다', status_code != 500, str(status_code))

    # 반사형 XSS: notFound 파라미터가 화면에 그대로 박히는가
    payload = '<script>alert(1)</script>'
    _, scan = client.get('/m/scan?notFound=' + urllib.parse.quote(payload))
    check('⚠️ 스캔 화면이 입력을 그대로 심지 않는다 (XSS)',
          '<script>alert(1)</script>' not in scan,
          '이스케이프됨' if payload not in scan else '원문 노출')

    # 검색어에 LIKE 와일드카드
    code2 = free_code(client)
    if code2:
        _, wild = client.get(f'/m/t/{code2}/attach?q=%25')
        rows = len(re.findall(r'class="m-row"', wild))
        check('검색어 %% 가 전부를 끌어오지 않는다(또는 30건으로 막힌다)', rows <= 30, f'{rows}건')

    # 남의 기관 자산을 번호에 붙이려는 시도
    if code2:
        token = client.csrf('/m/scan')
        s, loc, body = client.post(f'/m/t/{code2}/attach', [
            ('itemId', '00000000-0000-0000-0000-000000000000'), ('_csrf', token)])
        check('없는 자산에 붙이면 404 (500 아님)', s in (404, 302), str(s))

    summary()


def summary():
    print(f'\n━━━ 결과 ━━━\n  PASS {len(PASS)} · FAIL {len(FAIL)}')
    for label in FAIL:
        print(f'  실패: {label}')
    sys.exit(1 if FAIL else 0)


if __name__ == '__main__':
    main()
