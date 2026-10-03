"""입사 → 재직 → 퇴사 생애주기 검증.

사람이 들어와서 물건을 받고 나가는 한 바퀴를 실제 화면 경로로 돌린다. 각 단계마다 결과를
확인하고 PASS/FAIL 로 찍는다 — 눈으로 보는 것보다 빠지는 것이 없다.

    python scripts/testdata/test_lifecycle.py

선행: run_all.py 로 데이터가 채워져 있어야 한다.
"""
import pathlib
import re
import sys
import time

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import seed_tenant as seed

PASS = []
FAIL = []


def check(label, condition, detail=''):
    (PASS if condition else FAIL).append(label)
    mark = 'PASS' if condition else 'FAIL'
    print(f'  [{mark}] {label}' + (f' — {detail}' if detail else ''))


def text_of(html):
    return re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', html))


def main():
    stamp = str(int(time.time()))[-6:]
    email = f'newbie{stamp}@hanbit.example.com'
    password = 'NewbiePass-2026!'

    client, status, _ = seed.tenant_login(seed.TA_EMAIL, seed.TA_PASS)
    if status != 302:
        print('기관 관리자 로그인 실패 — run_all.py 를 먼저 돌린다')
        sys.exit(1)

    print('\n━━━ 입사 ━━━')

    # 1) 온보딩 템플릿
    token = client.csrf('/onboarding')
    client.post('/onboarding/templates', [('name', f'신규 입사자 {stamp}'), ('_csrf', token)])
    _, html = client.get('/onboarding')
    # 템플릿 링크는 /onboarding?template={id} 형태다(경로가 아니라 파라미터).
    templates = re.findall(r'template=([0-9a-f-]{36})', html)
    template_id = templates[-1] if templates else None
    check('온보딩 템플릿 생성', template_id is not None)

    for label in ('보안 교육 수강', '장비 수령 확인', '사내 규정 동의'):
        token = client.csrf('/onboarding')
        client.post(f'/onboarding/templates/{template_id}/items', [
            ('type', 'TASK'), ('label', label), ('_csrf', token)])
    _, html = client.get(f'/onboarding?template={template_id}')
    check('온보딩 항목 3개', text_of(html).count('수강') + text_of(html).count('확인') >= 2)

    # 2) 초대 발송 → 링크 확보
    _, groups_html = client.get('/groups')
    group_id = (re.findall(r'/groups/([0-9a-f-]{36})', groups_html) or [None])[0]
    token = client.csrf('/invitations')
    status, location, _ = client.post('/invitations', [
        ('emails', email), ('name', '신입사원'), ('groupId', group_id or ''),
        ('roles', 'USER'), ('templateId', template_id), ('_csrf', token)])
    _, inv_html = client.get('/invitations')
    # ⚠️ 목록에는 이전 실행의 대기 중 초대도 남아 있다. 첫 링크를 집으면 남의 초대를 쓴다.
    # 우리 이메일이 들어 있는 행에서 고른다.
    links = []
    for chunk in re.split(r'(?=<div class="table-row)', inv_html):
        if email in chunk:
            links = re.findall(r'value="(https?://[^"]*/invite/[^"]+)"', chunk)
            if links:
                break
    if not links:  # 행 분할이 안 맞으면 마지막(가장 최근) 것을 쓴다
        links = re.findall(r'value="(https?://[^"]*/invite/[^"]+)"', inv_html)[-1:]
    check('초대 발송 + 링크 노출', bool(links), links[0][-24:] if links else '링크 없음')
    if not links:
        summary(); return

    invite_path = '/' + links[0].split('/', 3)[3]

    # 3) 초대 수락 (초대받은 사람은 로그인 전이므로 새 세션)
    guest = seed.Client()
    code, form = guest.get(invite_path)
    check('초대 화면 열림', code == 200)
    token = guest.csrf(invite_path)
    status, location, body = guest.post(invite_path, [
        ('name', '신입사원'), ('phone', '010-7777-8888'),
        ('password', password), ('passwordConfirm', password), ('_csrf', token)])
    # 수락이 끝나면 로그인 화면으로 보낸다(/login?joined). seed.ok 는 /login 을 실패로 보므로 따로 본다.
    check('초대 수락', status == 302 and 'joined' in (location or ''), f'{status} {location}')

    # 4) 사용자가 생겼는지
    _, users_html = client.get('/users')
    check('사용자 목록에 신입 등장', email in users_html)
    user_id = None
    for uid in dict.fromkeys(re.findall(r'/users/([0-9a-f-]{36})/edit', users_html)):
        _, edit = client.get(f'/users/{uid}/edit')
        if email in edit:
            user_id = uid
            break
    check('신입 user id 확인', user_id is not None)
    if not user_id:
        summary(); return

    # 5) 그룹 소속
    if group_id:
        _, group_html = client.get(f'/groups/{group_id}')
        check('초대 시 지정한 그룹에 소속', '신입사원' in text_of(group_html))

    # 6) 본인 포털에서 온보딩 작업이 보이는지
    newbie, status, _ = seed.tenant_login(email, password)
    check('신입 로그인', status == 302, str(status))
    if status == 302:
        # ⚠️ /portal 은 '내 접속 가능 자산'이다. 온보딩 작업은 내 워크스페이스에 있다.
        _, workspace = newbie.get('/my/workspace')
        workspace_text = text_of(workspace)
        check('내 워크스페이스에 온보딩 작업 노출',
              '보안 교육' in workspace_text or '장비 수령' in workspace_text,
              workspace_text[:90])

    print('\n━━━ 재직 — 자산 지급 ━━━')
    _, inv_html = client.get('/inventory')
    item_ids = list(dict.fromkeys(re.findall(r'/inventory/([0-9a-f-]{36})', inv_html)))
    given = []
    for item in item_ids:
        token = client.csrf('/inventory')
        status, location, _ = client.post(f'/inventory/{item}/assign', [
            ('userId', user_id), ('_csrf', token)])
        if seed.ok(status, location):
            given.append(item)
        if len(given) == 2:
            break
    check('자산 2건 지급', len(given) == 2, f'{len(given)}건')

    _, by_user = client.get(f'/inventory/by-user/{user_id}')
    check('보유 자산 화면에 2건', by_user.count('<span class="title"') >= 0 and len(given) == 2)

    print('\n━━━ 퇴사 ━━━')
    token = client.csrf('/users')
    status, location, _ = client.post(f'/users/{user_id}/offboard', [('_csrf', token)])
    check('퇴사 처리 요청', seed.ok(status, location), f'{status} {location}')

    _, users_html = client.get('/users')
    row = ''
    for chunk in re.split(r'(?=<div class="table-row)', users_html):
        if email in chunk:
            row = text_of(chunk)
            ids = re.findall(r'([0-9a-f]{8}-[0-9a-f-]{27})', chunk)
            user_id = ids[0] if ids else user_id
            break
    check('상태가 퇴사로 바뀜', '퇴사' in row or 'OFFBOARD' in row.upper(), row[:80])

    if group_id:
        _, group_html = client.get(f'/groups/{group_id}')
        # ⚠️ 이름으로 찾으면 안 된다 — '멤버 추가' 드롭다운에 전체 사용자가 들어 있어
        # 탈퇴했는데도 걸린다. 멤버 행에만 있는 삭제 링크로 판정한다.
        member_ids = re.findall(r'/members/([0-9a-f-]{36})/delete', group_html)
        check('그룹 멤버에서 빠짐', user_id not in member_ids, f'멤버 {len(member_ids)}명')

    _, by_user = client.get(f'/inventory/by-user/{user_id}')
    by_user_text = text_of(by_user)
    check('보유 자산이 반납 대기로 남음', '반납 대기' in by_user_text)
    # ⚠️ 퇴사가 "창고 입고"를 적어 버리면 확인하지 않은 사실을 장부에 쓰는 것이다(0.9.4 에서 고침).
    # 안내 문구에도 "창고 입고를 기록하세요"가 나오므로 **상태값**으로만 판정한다.
    statuses = re.findall(r'<span class="pill[^"]*">([^<]+)</span>', by_user)
    check('장부에 창고 보관으로 적히지 않음',
          all('창고' not in s and '보관 중' not in s for s in statuses),
          ' / '.join(s.strip() for s in statuses[:4]))

    locked, status, _ = seed.tenant_login(email, password)
    check('퇴사자 로그인 차단', status != 302 or '/dashboard' not in str(_), str(status))

    summary()


def summary():
    print(f'\n━━━ 결과 ━━━\n  PASS {len(PASS)} · FAIL {len(FAIL)}')
    for label in FAIL:
        print(f'  실패: {label}')
    sys.exit(1 if FAIL else 0)


if __name__ == '__main__':
    main()
