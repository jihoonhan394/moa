"""
솔루션 헬스 체크 → 알림을 **실제로 돌고 있는 서버**에 대고 확인한다.

단위·통합 테스트는 `SolutionHealthService` 를 직접 불러 검증한다. 이 스크립트가 보는 것은
그걸로 못 보는 것 — <b>스케줄러가 진짜 깨어나는지</b>, 화면에 배지가 붙는지, 알림이 모바일
알림 화면까지 오는지다.

쓰는 법 (서버가 8081 에 떠 있어야 하고, 배치가 켜져 있어야 한다):

    python scripts\\testdata\\test_health_alert.py

흐름
  1) 로컬에 포트를 하나 연다(아무도 안 쓰는 번호)
  2) 그 포트를 가리키는 솔루션을 등록한다 → 배치 한 바퀴 뒤 '정상'
  3) 포트를 닫는다 → 연속 2회 실패해야 하므로 두 바퀴 뒤 '장애' + 알림

⚠️ 배치 주기가 기본 60초라 **3~4분 걸린다**. 급하면 서버를 이렇게 띄운다:
    --moa.solution.health-interval-ms=5000
"""
import re
import socket
import sys
import time

sys.path.insert(0, "scripts/testdata")
from seed_tenant import TA_EMAIL, TA_PASS, tenant_login, ok  # noqa: E402

PASS, FAIL = [], []


def check(label, condition, detail=""):
    (PASS if condition else FAIL).append(label)
    mark = "OK  " if condition else "FAIL"
    print(f"  [{mark}] {label}" + (f" — {detail}" if detail and not condition else ""))
    return condition


def free_port():
    """번호만 얻는다. 닫은 뒤라 아무도 듣지 않는다."""
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def first_asset_id(client):
    """
    서버 자산 하나. 솔루션은 자산에 속한다.

    ⚠️ `/assets` 화면을 긁지 않는다. 그 페이지에는 자산 말고도 36자 UUID 가 섞여 있어서
    엉뚱한 id 를 집으면 등록이 **404** 로 떨어진다(실제로 그랬다). 솔루션 등록 폼의
    `assetId` 선택지가 정답이다 — 서버가 받아 주는 목록 그 자체다.
    """
    _, html = client.get("/solutions")
    select = re.search(r'name="assetId".*?</select>', html, re.S)
    if not select:
        return None
    options = re.findall(r'value="([0-9a-f-]{36})"', select.group(0))
    return options[0] if options else None


def solution_row(client, name):
    """솔루션 목록에서 그 솔루션이 있는 표 행(배지가 그 안에 있다)."""
    _, html = client.get("/solutions")
    # 행은 <div class="table-row"> … </div> 하나다. 이름이 들어 있는 행만 꺼낸다.
    for row in re.findall(r'<div class="table-row">(.*?)</div>\s*(?=<div class="table-row">|</div>)',
                          html, re.S):
        if name in row:
            return row
    # 마지막 행은 위 lookahead 로 안 잡힌다 — 이름으로 거칠게 한 번 더 찾는다.
    index = html.find(name)
    return html[max(0, index - 200):index + 1200] if index >= 0 else ""


def badge_of(row):
    match = re.search(r'class="health-badge ([a-z]+)"', row)
    return match.group(1) if match else None


def wait_for_badge(client, name, wanted, rounds, interval=20):
    """배치가 돌 때까지 기다린다. 주기를 모르니 넉넉히 센다."""
    for attempt in range(rounds):
        badge = badge_of(solution_row(client, name))
        if badge == wanted:
            return True, badge
        print(f"      … {attempt * interval}초 경과, 지금 배지={badge}")
        time.sleep(interval)
    return False, badge_of(solution_row(client, name))


def main():
    client, status, location = tenant_login(TA_EMAIL, TA_PASS)
    if "/login" in (location or "") or "/enter" in (location or ""):
        sys.exit("로그인 실패 — 서버와 시드 데이터를 확인하세요.")
    print(f"로그인: {status} -> {location}")

    asset_id = first_asset_id(client)
    if not asset_id:
        sys.exit("서버 자산이 없습니다. run_all.py 를 먼저 돌리세요.")

    port = free_port()
    name = f"헬스시험-{port}"
    print(f"\n1) 포트 {port} 를 열고 솔루션 '{name}' 등록")

    listener = socket.socket()
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(("127.0.0.1", port))
    listener.listen(5)

    try:
        token = client.csrf("/solutions")
        status, location, body = client.post("/solutions", [
            ("assetId", asset_id), ("name", name),
            ("type", "LINUX_DAEMON"), ("identifier", f"health-{port}.service"),
            ("healthCheckType", "TCP_PORT"), ("healthCheckTarget", f"127.0.0.1:{port}"),
            ("status", "ACTIVE"), ("controlProtocol", "SSH"), ("_csrf", token)])
        # ⚠️ 302 가 /enter·/login 이면 인증이 끊긴 것이다 — 성공이 아니다.
        if not check("솔루션 등록", ok(status, location), f"{status} -> {location}"):
            print("      본문:", re.sub(r"\s+", " ", body)[:300])
            return

        print("\n2) 배치를 기다린다 — 열린 포트는 '정상' 이어야 한다")
        found, badge = wait_for_badge(client, name, "up", rounds=5)
        check("열린 포트 → 정상 배지", found, f"배지={badge}")

        print("\n3) 포트를 닫는다 — 연속 2회 실패해야 '장애' 다")
        listener.close()
        listener = None
        found, badge = wait_for_badge(client, name, "down", rounds=8)
        check("닫힌 포트 → 장애 배지", found, f"배지={badge}")

        row = solution_row(client, name)
        check("장애 사유가 화면에 있다", str(port) in row or "접속하지 못" in row)

        print("\n4) 알림이 왔나 (모바일 알림 화면)")
        _, notifications = client.get("/m/notifications")
        check("장애 알림 도착", f"[장애] {name}" in notifications)
        # 링크는 PC 경로로 저장되고 모바일 화면이 /m/** 로 바꿔 띄운다.
        check("알림 링크가 모바일 경로", "/m/solutions/" in notifications)

        print("\n5) 모바일 솔루션 목록에서 장애가 위로 온다")
        _, mobile = client.get("/m/solutions")
        check("응답하지 않는다는 안내", "응답하지 않습니다" in mobile)

        # ⚠️ "내 솔루션이 첫 줄"이 아니다. 정렬은 상태(DOWN→UNKNOWN→UP) 다음에 이름이므로,
        # 다른 장애 솔루션이 이름순으로 앞설 수 있다. 봐야 하는 것은 **장애가 전부 위에**
        # 모였는가와, 내 것이 그 묶음 안에 있는가다.
        #
        # ⚠️ 이름과 배지를 한 정규식으로 잇지 않는다. 배지 조각은 바깥 <span> 래퍼를 하나 더
        # 내므로(표 칸이 비지 않게 하려고 그렇게 뒀다) 이름 바로 뒤에 배지가 오지 않는다.
        # 행(<li>) 단위로 끊어서 그 안을 각각 찾는 편이 마크업 변화에 덜 깨진다.
        order = []
        for row in re.findall(r"<li>.*?</li>", mobile, re.S):
            title = re.search(r'<span class="title">\s*<span[^>]*>([^<]+)</span>', row)
            badge = re.search(r'class="health-badge (\w+)"', row)
            if title:
                order.append((title.group(1).strip(), badge.group(1) if badge else None))
        rank = {"down": 0, "unknown": 1, "up": 2}
        ranks = [rank.get(badge, 1) for _, badge in order]
        check("상태 순으로 정렬돼 있다", ranks == sorted(ranks), f"순서={ranks}")
        mine = [badge for title, badge in order if title == name]
        check("내 솔루션이 장애 묶음에 있다", mine == ["down"], f"내 배지={mine}")

    finally:
        if listener is not None:
            listener.close()

    print(f"\n결과: {len(PASS)} 통과 / {len(FAIL)} 실패")
    for failure in FAIL:
        print(f"  - {failure}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
