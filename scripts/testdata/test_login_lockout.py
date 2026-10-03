"""출발지 잠금이 X-Forwarded-For 위조로 우회되는지 확인한다.

    python scripts/testdata/test_login_lockout.py

⚠️ 이 스크립트는 **현재 출발지를 15분 잠근다**(시간 경과로만 풀린다). 로컬은 H2 메모리라
서버를 다시 띄우면 초기화된다 — 다른 시험을 이어서 할 거면 끝나고 재기동한다.

배경: X-Forwarded-For 는 클라이언트가 보낼 수 있는 헤더다. 그대로 믿으면
  ① 값을 바꿔 가며 보내 출발지 잠금(20회/15분)을 무한히 우회하고
  ② 남의 IP 를 적어 감사 기록을 오염시킨다.
그래서 `moa.trusted-proxy-count` 로 믿을 홉 수를 명시하고(기본 0), 체인 뒤에서부터 센다.
"""
import pathlib
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

BASE = 'http://localhost:8081'
TENANT_CODE = 'MOARA'  # 샘플 기관. 계정이 있든 없든 실패 자체는 집계된다.
GOOD_USER = 'dev.kim@example.com'
GOOD_PASSWORD = 'LocalDemo2026!'
IP_LIMIT = 20

sys.path.insert(0, str(pathlib.Path(__file__).parent))

PASS, FAIL = [], []


def check(label, condition, detail=''):
    (PASS if condition else FAIL).append(label)
    mark = 'PASS' if condition else 'FAIL'
    print(f'  [{mark}] {label}' + (f' — {detail}' if detail else ''))


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def login(username, password, spoofed_ip):
    """로그인을 한 번 시도하고 리다이렉트 위치를 돌려준다."""
    jar = CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect())

    def get(path):
        request = urllib.request.Request(BASE + path, method='GET')
        if spoofed_ip:
            request.add_header('X-Forwarded-For', spoofed_ip)
        try:
            with opener.open(request, timeout=20) as response:
                return response.read().decode('utf-8', 'replace')
        except urllib.error.HTTPError as error:
            return error.read().decode('utf-8', 'replace')

    def post(path, fields):
        body = urllib.parse.urlencode(fields, encoding='utf-8').encode()
        request = urllib.request.Request(
            BASE + path, data=body, method='POST',
            headers={'Content-Type': 'application/x-www-form-urlencoded'})
        if spoofed_ip:
            request.add_header('X-Forwarded-For', spoofed_ip)
        try:
            with opener.open(request, timeout=20) as response:
                return response.headers.get('Location', '')
        except urllib.error.HTTPError as error:
            return error.headers.get('Location', '')

    html = get('/enter')
    token = re.search(r'name="_csrf" value="([^"]+)"', html)
    post('/enter', [('code', TENANT_CODE), ('_csrf', token.group(1) if token else '')])
    html = get('/login')
    token = re.search(r'name="_csrf" value="([^"]+)"', html)
    return post('/login', [
        ('username', username), ('password', password),
        ('_csrf', token.group(1) if token else '')])


def main():
    print('━━━ 출발지 잠금 우회 시도 ━━━')
    print('  화면은 "잠김"과 "비밀번호 오류"를 같은 문구로 보여 준다(잠금 상태를 알려 주지 않는다).')
    print('  그래서 **올바른 비밀번호로 로그인되는지**로 판정한다.')

    before = login(GOOD_USER, GOOD_PASSWORD, None)
    check('시작 전에는 정상 로그인된다', 'dashboard' in (before or ''), before or '(없음)')
    if 'dashboard' not in (before or ''):
        print('  전제가 깨졌다 — 서버를 갓 띄운 상태에서 돌린다(샘플 데이터 필요).')
        sys.exit(1)

    print(f'  {IP_LIMIT + 2}회 실패시킨다(매번 다른 위조 IP)...')
    for n in range(IP_LIMIT + 2):
        login(f'ghost{n}@example.com', 'definitely-wrong-password', f'203.0.113.{n % 250}')

    after = login(GOOD_USER, GOOD_PASSWORD, '203.0.113.123')
    bypassed = 'dashboard' in (after or '')
    check('위조 헤더로 출발지 잠금을 우회할 수 없다',
          not bypassed,
          '우회 성공 — 잠기지 않았다' if bypassed else f'잠김 ({after})')

    print(f'  결과: PASS {len(PASS)} · FAIL {len(FAIL)}')
    print('  ⚠️ 이 출발지는 15분간 잠겼다. 이어서 시험하려면 서버를 다시 띄운다.')
    sys.exit(1 if FAIL else 0)


if __name__ == '__main__':
    main()
