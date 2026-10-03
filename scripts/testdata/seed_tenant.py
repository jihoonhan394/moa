"""
중소·중견 규모를 가정한 테스트 기관 시딩.

MOARA(데모) 말고 별도 기관을 만들어 실제 볼륨을 넣는다.
  사용자 32명 (자산/경영지원 5 · 인프라 관리자 1 + 인프라팀 5 · 일반 21)
  서버 12대 · 솔루션 24개 · 자산 120개 · 관리번호 140장
"""
import json
import random
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from http.cookiejar import CookieJar

BASE = "http://localhost:8081"
ADMIN_USER = "admin"
ADMIN_PASS = "BootstrapAdmin-2026!"
TENANT_NAME = "한빛산업"
TENANT_CODE = "HANBIT"
TA_EMAIL = "admin@hanbit.example.com"
TA_PASS = "HanbitAdmin-2026!"
USER_PASS = "HanbitUser-2026!"

random.seed(20261003)


class Client:
    def __init__(self):
        self.jar = CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar),
            NoRedirect(),
        )

    def get(self, path):
        request = urllib.request.Request(BASE + path, method="GET")
        try:
            with self.opener.open(request, timeout=30) as response:
                return response.status, response.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as error:
            return error.code, error.read().decode("utf-8", "replace")

    def post(self, path, fields):
        body = urllib.parse.urlencode(
            [(k, "" if v is None else str(v)) for k, v in fields], encoding="utf-8"
        ).encode()
        request = urllib.request.Request(
            BASE + path, data=body, method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        try:
            with self.opener.open(request, timeout=30) as response:
                return response.status, response.headers.get("Location", ""), response.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as error:
            return error.code, error.headers.get("Location", ""), error.read().decode("utf-8", "replace")

    def csrf(self, path):
        _, html = self.get(path)
        match = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
        if not match:
            match = re.search(r'name="_csrf"\s+content="([^"]+)"', html)
        return match.group(1) if match else None


def ok(status, location):
    """302 라도 /enter·/login 으로 튕기면 인증이 끊긴 것이다 — 성공이 아니다."""
    return status == 302 and "/enter" not in (location or "") and "/login" not in (location or "")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def platform_login():
    client = Client()
    client.get("/platform")  # 은닉 진입 — 세션에 플랫폼 플래그
    token = client.csrf("/login")
    status, location, _ = client.post("/login", [
        ("username", ADMIN_USER), ("password", ADMIN_PASS), ("_csrf", token)])
    print(f"  플랫폼 로그인: {status} -> {location}")
    return client


def tenant_login(email, password):
    client = Client()
    token = client.csrf("/enter")
    client.post("/enter", [("code", TENANT_CODE), ("_csrf", token)])
    token = client.csrf("/login")
    status, location, _ = client.post("/login", [
        ("username", email), ("password", password), ("_csrf", token)])
    return client, status, location


def main():
    print("1) 플랫폼 관리자로 기관 생성")
    platform = platform_login()
    token = platform.csrf("/admin/tenants")
    status, location, _ = platform.post("/admin/tenants", [
        ("name", TENANT_NAME), ("code", TENANT_CODE), ("_csrf", token)])
    print(f"  기관 생성: {status} -> {location}")
    tenant_id = location.rstrip("/").split("/")[-1] if location else None
    if not tenant_id or len(tenant_id) != 36:
        _, html = platform.get("/admin/tenants")
        found = re.findall(r'/admin/tenants/([0-9a-f-]{36})', html)
        tenant_id = found[-1] if found else None
    print(f"  기관 id: {tenant_id}")

    token = platform.csrf(f"/admin/tenants/{tenant_id}")
    status, location, body = platform.post(f"/admin/tenants/{tenant_id}/admins", [
        ("username", "hanbit.admin"), ("name", "한빛 관리자"), ("email", TA_EMAIL),
        ("phone", "02-1234-5678"), ("password", TA_PASS), ("passwordConfirm", TA_PASS),
        ("_csrf", token)])
    print(f"  기관 관리자 생성: {status} -> {location}")
    if status >= 400 or ("오류" in body and status == 200):
        print("  ! 관리자 생성 실패 — 중단"); sys.exit(1)

    print("1-1) 기관 기능 모듈 켜기 (신규 기관은 전부 꺼진 채로 생성된다)")
    features = ["ASSETS", "SERVER_ACCESS", "SOLUTIONS", "CREDENTIALS",
                "INVENTORY", "RESERVATION", "WIKI"]
    token = platform.csrf(f"/admin/tenants/{tenant_id}")
    status, location, _ = platform.post(f"/admin/tenants/{tenant_id}", [
        ("unlimited", "true"), ("_csrf", token)] + [("features", f) for f in features])
    print(f"  기능 켜기: {status} -> {location}")

    print("2) 기관 관리자로 로그인")
    client, status, location = tenant_login(TA_EMAIL, TA_PASS)
    print(f"  로그인: {status} -> {location}")
    if status != 302:
        print("  ! 로그인 실패 — 중단"); sys.exit(1)

    print("3) 사용자 32명")
    teams = (
        [("자산", "경영지원팀", 5), ("인프라장", "인프라관리실", 1),
         ("인프라", "인프라관리실", 5), ("일반", "사업부", 21)]
    )
    surnames = "김이박최정강조윤장임한오서신권황안송류전홍고문양손배백허유남심노"
    givens = ["민준", "서연", "도윤", "지우", "하준", "서준", "하은", "지호", "예준", "수아",
              "지안", "유진", "다현", "성민", "채원", "현우", "지민", "소율", "건우", "나윤",
              "준서", "서윤", "우진", "가은", "시우", "하율", "태양", "은서", "재윤", "주원",
              "라온", "유찬"]
    role_of = {
        "자산": ["ASSET_MANAGER"],
        "인프라장": ["INFRA_MANAGER", "TENANT_ADMIN"],
        "인프라": ["INFRA_MANAGER"],
        "일반": [],
    }
    created = []
    index = 0
    for kind, team, count in teams:
        for _ in range(count):
            name = surnames[index % len(surnames)] + givens[index % len(givens)]
            username = f"{kind.lower()}{index:02d}"
            email = f"user{index:02d}@hanbit.example.com"
            token = client.csrf("/users")
            status, location, body = client.post("/users", [
                ("username", username), ("name", name), ("email", email),
                ("phone", f"010-{1000 + index:04d}-{2000 + index:04d}"),
                ("password", USER_PASS), ("passwordConfirm", USER_PASS),
                ("status", "ACTIVE"), ("_csrf", token)]
                + [("roles", r) for r in role_of[kind]])
            if ok(status, location):
                created.append((email, name, kind, team))
            else:
                print(f"  ! 사용자 {email} 실패 {status}")
            index += 1
    print(f"  생성: {len(created)}명")

    print("3-1) 기관 관리자에게 관리 역할 부여")
    _, html = client.get("/users")
    rows = re.findall(r'/users/([0-9a-f-]{36})', html)
    me = None
    for uid in dict.fromkeys(rows):
        _, form = client.get(f"/users/{uid}/edit")
        if TA_EMAIL in form:
            me = uid
            break
    if me:
        token = client.csrf(f"/users/{me}/edit")
        status, location, _ = client.post(f"/users/{me}", [
            ("username", "hanbit.admin"), ("name", "한빛 관리자"), ("email", TA_EMAIL),
            ("phone", "02-1234-5678"), ("password", ""), ("passwordConfirm", ""),
            ("status", "ACTIVE"), ("_csrf", token),
            ("roles", "TENANT_ADMIN"), ("roles", "INFRA_MANAGER"), ("roles", "ASSET_MANAGER")])
        print(f"  역할 부여: {status} -> {location}")
        # 역할은 세션의 권한에 반영되지 않으므로 다시 로그인한다.
        client, status, location = tenant_login(TA_EMAIL, TA_PASS)
        print(f"  재로그인: {status}")
    else:
        print("  ! 관리자 user id 를 찾지 못했다")

    print("4) 자산 카테고리")
    token = client.csrf("/inventory/categories")
    _, html = client.get("/inventory/categories")
    roots = dict(re.findall(r'data-category-id="([0-9a-f-]{36})"[^>]*>\s*([^<]+)', html))
    tree = client.get("/inventory")[1]
    def root_id(label):
        match = re.search(r'value="([0-9a-f-]{36})"[^>]*>\s*' + re.escape(label), tree)
        return match.group(1) if match else None

    print("5) 서버 12대")
    server_ids = []
    for n in range(1, 13):
        token = client.csrf("/assets")
        status, location, body = client.post("/assets", [
            ("name", f"srv-{n:02d}.hanbit.local"),
            ("assetType", "SERVER"),
            ("protocol", "SSH" if n % 3 else "RDP"),
            ("host", f"10.20.{n // 10}.{100 + n}"),
            ("port", 22 if n % 3 else 3389),
            ("osType", "Linux" if n % 3 else "Windows"),
            ("category", ""),
            ("cpu", f"{4 * (1 + n % 4)} vCPU"), ("ram", f"{8 * (1 + n % 4)}GB"),
            ("disk", f"{250 * (1 + n % 3)}GB SSD"),
            ("hwModel", random.choice(["DL380 Gen10", "R740xd", "KVM", "VMware"])),
            ("description", f"{random.choice(['사내', 'DMZ', '백오피스'])} 서버"),
            ("status", "ACTIVE"), ("_csrf", token)])
        if status != 302:
            print(f"  ! 서버 {n} 실패 {status}")
    _, html = client.get("/assets")
    server_ids = list(dict.fromkeys(re.findall(r'/assets/([0-9a-f-]{36})', html)))
    print(f"  서버: {len(server_ids)}대")

    print("6) 솔루션 24개")
    catalog = [
        ("Nginx", "LINUX_DAEMON", "nginx"), ("PostgreSQL", "LINUX_DAEMON", "postgresql"),
        ("Redis", "LINUX_DAEMON", "redis"), ("Jenkins", "LINUX_DAEMON", "jenkins"),
        ("Tomcat", "LINUX_DAEMON", "tomcat"), ("GitLab", "DOCKER_CONTAINER", "gitlab"),
        ("Grafana", "DOCKER_CONTAINER", "grafana"), ("Prometheus", "DOCKER_CONTAINER", "prometheus"),
        ("Elasticsearch", "LINUX_DAEMON", "elasticsearch"), ("Kafka", "LINUX_DAEMON", "kafka"),
        ("MinIO", "DOCKER_CONTAINER", "minio"), ("Keycloak", "DOCKER_CONTAINER", "keycloak"),
        ("백업 에이전트", "WINDOWS_SERVICE", "BackupAgent"), ("AD 동기화", "WINDOWS_SERVICE", "ADSync"),
        ("그룹웨어", "WINDOWS_SERVICE", "Groupware"), ("ERP 배치", "WINDOWS_SERVICE", "ErpBatch"),
        ("회계 시스템", "WINDOWS_SERVICE", "AccountSvc"), ("문서중앙화", "WINDOWS_SERVICE", "DocCentral"),
        ("메일 게이트웨이", "LINUX_DAEMON", "postfix"), ("DNS", "LINUX_DAEMON", "named"),
        ("NAC", "WINDOWS_SERVICE", "NacAgent"), ("DLP", "WINDOWS_SERVICE", "DlpAgent"),
        ("백신 관리", "WINDOWS_SERVICE", "AvConsole"), ("로그 수집", "LINUX_DAEMON", "rsyslog"),
    ]
    vendors = [("(주)한빛시스템", "02-555-0101", "유지보수 계약 2027-03 까지"),
               ("오픈소스 커뮤니티", "support@oss.example.com", "자체 대응"),
               ("(주)누리소프트", "070-8888-1234", "연 2회 정기점검"),
               ("클라우드메이트", "1588-0000", "24시간 장애 접수")]
    solution_count = 0
    for n, (name, stype, ident) in enumerate(catalog):
        if not server_ids:
            break
        asset_id = server_ids[n % len(server_ids)]
        vendor = vendors[n % len(vendors)]
        token = client.csrf("/solutions")
        status, location, body = client.post("/solutions", [
            ("assetId", asset_id), ("name", name), ("type", stype), ("identifier", ident),
            ("healthCheckType", "TCP_PORT" if n % 2 else "COMMAND"),
            ("healthCheckTarget", str(8000 + n) if n % 2 else f"systemctl is-active {ident}"),
            ("status", "ACTIVE" if n % 7 else "DISABLED"),
            ("controlProtocol", "WINRM" if stype.startswith("WINDOWS") else "SSH"),
            ("controlPort", 5985 if stype.startswith("WINDOWS") else 22),
            ("category", ""), ("_csrf", token)])
        if ok(status, location):
            solution_count += 1
        elif n == 0:
            print(f"  ! 솔루션 실패 {status}: {body[:300]}")
    print(f"  솔루션: {solution_count}개")

    print("7) 자산 120개")
    kinds = [
        ("노트북", "실물 / 사무기기 / 컴퓨터", ["LG그램 16", "삼성 갤럭시북4", "맥북에어 M3", "ThinkPad X1"]),
        ("모니터", "실물 / 사무기기 / 모니터", ["LG 27UL850", "삼성 S27A600", "Dell U2723QE"]),
        ("프린터", "실물 / 사무기기 / 프린터", ["HP M404dn", "신도리코 N600"]),
        ("데스크톱", "실물 / 사무기기 / 컴퓨터", ["HP ProDesk 600", "조립 PC"]),
    ]
    _, inv_html = client.get("/inventory")
    options = re.findall(r'<option value="([^"]+)"', inv_html)
    paths = [o for o in options if " / " in o]
    if not paths:
        print("  ! 자산 카테고리 경로를 찾지 못했다. 등록을 건너뛴다.")
        paths = []
    made = 0
    for n in range(120):
        kind, _, models = kinds[n % len(kinds)]
        model = models[n % len(models)]
        path = paths[n % len(paths)] if paths else ""
        token = client.csrf("/inventory")
        status, location, body = client.post("/inventory", [
            ("name", f"{kind} {model} #{n + 1:03d}"),
            ("category", path),
            ("serialNo", f"SN{2026}{n:05d}"),
            ("quantity", 1),
            ("note", random.choice(["", "", "3층 창가", "본사 2층", "지사 반출", "예비"])),
            ("_csrf", token)])
        if ok(status, location):
            made += 1
        elif n == 0:
            print(f"  ! 자산 실패 {status}: {body[:300]}")
    print(f"  자산: {made}개")

    print("8) 관리번호 140장")
    token = client.csrf("/inventory/tags")
    status, _, _ = client.post("/inventory/tags", [("count", 140), ("_csrf", token)])
    print(f"  발급: {status}")

    print("\n완료")
    print(f"  기관코드 {TENANT_CODE} / 관리자 {TA_EMAIL} / 사용자 비밀번호는 스크립트 상수")


if __name__ == "__main__":
    main()
