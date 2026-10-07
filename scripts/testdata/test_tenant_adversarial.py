"""
기관(테넌트) 생성과 진입을 **이상한 입력·이상한 방법**으로 찔러 본다.

여기서 보는 것은 "거부되는가"가 아니라 셋이다.

  1) 500 이 나가지 않는가        — 서버 오류는 사용자가 할 수 있는 게 없다
  2) 거부 사유가 **화면에 보이는가** — AGENTS.md: 서버측 검증 오류는 화면에 보여야 한다
  3) 저장된 값이 **그대로 실행되지 않는가** — XSS

쓰는 법 (서버가 8081 에 떠 있어야 한다):

    python scripts\\testdata\\test_tenant_adversarial.py

만든 기관은 코드가 `ZZ` 로 시작한다 — 목록에서 골라 지우기 쉽게.
"""
import re
import sys
import uuid

sys.path.insert(0, "scripts/testdata")
from seed_tenant import ADMIN_PASS, ADMIN_USER, Client, ok  # noqa: E402

PASS, FAIL, NOTES = [], [], []


def check(label, condition, detail=""):
    (PASS if condition else FAIL).append(label)
    mark = "OK  " if condition else "FAIL"
    print(f"  [{mark}] {label}" + (f" -- {detail}" if detail and not condition else ""))
    return condition


def note(text):
    NOTES.append(text)
    print(f"  [NOTE] {text}")


def platform():
    client = Client()
    client.get("/platform")
    status, location, _ = client.post("/login", [
        ("username", ADMIN_USER), ("password", ADMIN_PASS), ("_csrf", client.csrf("/login"))])
    if "/admin" not in (location or ""):
        sys.exit(f"플랫폼 로그인 실패: {status} -> {location}")
    return client


def create_tenant(client, name, code):
    """기관 생성 시도. (status, location, body) 를 돌려준다."""
    return client.post("/admin/tenants", [
        ("name", name), ("code", code), ("_csrf", client.csrf("/admin/tenants"))])


def field_errors(body):
    """폼 오류 문구를 긁는다. 화면에 '보이는지'를 보는 것이므로 렌더된 HTML 에서 찾는다."""
    found = re.findall(r'class="[^"]*(?:field-error|error-message|t-bad)[^"]*"[^>]*>([^<]+)<', body)
    found += re.findall(r'<li>([^<]*(?:없|않|초과|이미|사용할|필수|입력)[^<]*)</li>', body)
    return [f.strip() for f in found if f.strip()]


def suffix():
    return uuid.uuid4().hex[:6].upper()


# ── 1. 거부되어야 하는 입력 ────────────────────────────────────────────────

def rejected_inputs(client):
    print("\n1) 거부되어야 하는 입력 — 500 이 아니라 화면 안내로")
    cases = [
        ("코드에 한글", "정상이름", "한빛"),
        ("코드에 공백", "정상이름", "ZZ " + suffix()),
        ("코드에 경로문자", "정상이름", "../ZZ" + suffix()),
        ("코드에 슬래시", "정상이름", "ZZ/" + suffix()),
        ("코드에 따옴표", "정상이름", "ZZ'\"" + suffix()),
        ("코드 비움", "정상이름", ""),
        ("코드 공백만", "정상이름", "   "),
        ("이름 비움", "", "ZZ" + suffix()),
        ("이름 공백만", "    ", "ZZ" + suffix()),
        ("이름 101자(한계 초과)", "가" * 101, "ZZ" + suffix()),
        ("코드 51자(한계 초과)", "정상이름", "Z" * 51),
        ("코드에 NUL 바이트", "정상이름", "ZZ\x00" + suffix()),
        ("코드에 개행", "정상이름", "ZZ\n" + suffix()),
        ("코드에 SQL 조각", "정상이름", "ZZ' OR '1'='1"),
    ]
    for label, name, code in cases:
        status, location, body = create_tenant(client, name, code)
        created = ok(status, location)
        if status >= 500:
            check(label, False, f"{status} 서버 오류")
            continue
        if created:
            check(label, False, f"거부되지 않고 생성됐다 -> {location}")
            continue
        errors = field_errors(body)
        check(f"{label} — 화면에 사유", bool(errors), f"{status}, 사유 문구를 못 찾음")


# ── 2. 통과하지만 이상한 값 ────────────────────────────────────────────────

def odd_but_allowed(client):
    print("\n2) 통과하는 이상한 값 — 저장·표시가 깨지지 않는지")
    created = []
    cases = [
        ("이름에 script 태그", "<script>alert('xss')</script>", "ZZX" + suffix()),
        ("이름에 img onerror", "<img src=x onerror=alert(1)>", "ZZI" + suffix()),
        ("이름에 이모지", "한빛 🏭 산업", "ZZE" + suffix()),
        ("이름에 제로폭 문자", "한​빛", "ZZZ" + suffix()),
        ("이름에 RTL 오버라이드", "‮HANBIT", "ZZR" + suffix()),
        ("이름 100자(한계)", "가" * 100, "ZZL" + suffix()),
        ("이름에 앞뒤 공백", "   공백기관   ", "ZZW" + suffix()),
        ("코드가 점·밑줄·하이픈", "구두점기관", "ZZ._-" + suffix()),
    ]
    for label, name, code in cases:
        status, location, body = create_tenant(client, name, code)
        if status >= 500:
            check(label, False, f"{status} 서버 오류")
            continue
        if not ok(status, location):
            note(f"{label}: 거부됐다(허용/거부 모두 설계 선택) — 사유 {field_errors(body)[:1]}")
            continue
        check(label, True)
        created.append((label, name, code, location.rsplit("/", 1)[-1]))
    return created


def xss_is_escaped(client, created):
    print("\n3) 저장된 값이 화면에서 실행되지 않는지(XSS)")
    _, listing = client.get("/admin/tenants")
    check("기관 목록에 <script> 원문이 없다", "<script>alert('xss')</script>" not in listing)
    check("기관 목록에 onerror 속성이 없다",
          not re.search(r"<img[^>]*onerror", listing, re.I))
    escaped = "&lt;script&gt;" in listing or "&amp;lt;script" in listing
    check("이스케이프되어 표시된다", escaped, "목록에서 이스케이프 흔적을 못 찾음")

    for label, name, code, tenant_id in created:
        if "script" not in name and "onerror" not in name:
            continue
        _, detail = client.get(f"/admin/tenants/{tenant_id}")
        check(f"기관 상세도 이스케이프 — {label}",
              name not in detail and not re.search(r"<img[^>]*onerror", detail, re.I))


# ── 3. 이상한 방법 ────────────────────────────────────────────────────────

def odd_methods(client, created):
    print("\n4) 이상한 방법 — 중복·우회·직접 접근")

    # 중복 코드(대소문자만 다르게)
    if created:
        _, _, code, _ = created[0]
        status, location, body = create_tenant(client, "중복시도", code.lower())
        check("대소문자만 다른 중복 코드는 거부", not ok(status, location))
        check("  사유가 화면에", any("이미" in e for e in field_errors(body)),
              f"사유: {field_errors(body)[:2]}")

    # CSRF 토큰 없이 생성
    status, location, _ = client.post("/admin/tenants", [
        ("name", "토큰없음"), ("code", "ZZNOCSRF" + suffix())])
    check("CSRF 토큰 없으면 거부(403)", status == 403, f"{status} -> {location}")

    # 없는 기관 상세 — 404 여야 한다(500 이면 안 된다)
    status, _ = client.get(f"/admin/tenants/{uuid.uuid4()}")
    check("없는 기관 상세는 404", status == 404, f"{status}")

    # UUID 가 아닌 id — 400 이어야 한다
    status, _ = client.get("/admin/tenants/not-a-uuid")
    check("UUID 가 아닌 id 는 500 이 아니다", status < 500, f"{status}")


def entry_flow(created):
    print("\n5) 새 기관으로 /enter 진입")
    if not created:
        note("생성된 기관이 없어 진입 시험을 건너뛴다")
        return None
    _, _, code, tenant_id = created[0]
    for variant, label in [(code, "그대로"), (code.lower(), "소문자"), (f"  {code}  ", "앞뒤 공백")]:
        c = Client()
        status, location, body = c.post("/enter", [("code", variant), ("_csrf", c.csrf("/enter"))])
        check(f"기관코드 {label} 로 진입", "/login" in (location or ""),
              f"{status} -> {location}")
    # 없는 코드
    c = Client()
    status, location, body = c.post("/enter", [("code", "ZZNOPE" + suffix()), ("_csrf", c.csrf("/enter"))])
    check("없는 기관코드는 화면에 사유", status == 200 and "존재하지 않는" in body,
          f"{status} -> {location}")
    return tenant_id


def features_on_by_default(client, tenant_id):
    print("\n6) 새 기관은 기능이 켜진 채로 생성된다")
    if tenant_id is None:
        return
    _, detail = client.get(f"/admin/tenants/{tenant_id}")
    checked = set(re.findall(r'name="features"[^>]*value="(\w+)"[^>]*checked', detail))
    offered = set(re.findall(r'name="features"[^>]*value="(\w+)"', detail))
    # 전에는 전부 꺼진 채로 생성돼, 관리자가 로그인해도 자산·서버·솔루션이 모두 403 이었다.
    check("기능 모듈이 전부 켜져 있다", bool(offered) and checked == offered,
          f"켜진 것 {sorted(checked)} / 전체 {sorted(offered)}")


def main():
    client = platform()
    rejected_inputs(client)
    created = odd_but_allowed(client)
    xss_is_escaped(client, created)
    odd_methods(client, created)
    tenant_id = entry_flow(created)
    features_on_by_default(client, tenant_id)

    print(f"\n결과: {len(PASS)} 통과 / {len(FAIL)} 실패 / 참고 {len(NOTES)}건")
    for failure in FAIL:
        print(f"  - 실패: {failure}")
    if created:
        print(f"\n만든 기관 {len(created)}개(코드가 ZZ 로 시작):")
        for label, _, code, tenant_id in created:
            print(f"  {code:16} {label}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
