"""
분류 검사가 네 경로에서 실제로 막는지 **돌고 있는 서버**에 대고 본다.

단위·통합 테스트는 서비스를 직접 부른다. 이 스크립트가 보는 것은 그걸로 못 보는 것 —
화면이 오류를 **보여 주는지**(서버측 검증은 화면에 보여야 한다, AGENTS.md)와,
짧은 이름이 전체 경로로 저장되는지다.

    python scripts\testdata\test_category_guard.py
"""
import re
import sys

sys.path.insert(0, "scripts/testdata")
from seed_tenant import TA_EMAIL, TA_PASS, ok, tenant_login  # noqa: E402

PASS, FAIL = [], []


def check(label, condition, detail=""):
    (PASS if condition else FAIL).append(label)
    print(f"  [{'OK  ' if condition else 'FAIL'}] {label}" + (f" — {detail}" if detail and not condition else ""))
    return condition


def leaf_paths(client):
    """등록 폼 셀렉트에 실제로 들어 있는 분류 경로."""
    _, html = client.get("/inventory")
    select = re.search(r'aria-label="카테고리[^"]*"(.*?)</select>', html, re.S)
    if not select:
        return []
    return [re.sub(r"&amp;", "&", v) for v in re.findall(r'value="([^"]+)"', select.group(1)) if v]


def item_row(client, name):
    """
    목록에서 그 자산의 **표 행**.

    ⚠️ 이름을 그냥 find 하면 안 된다. 같은 화면의 "부품 장착" 드롭다운에 모든 자산 이름이
    <option> 으로 들어 있어서, 첫 일치는 거의 항상 그 드롭다운이다(실제로 그렇게 틀렸다).
    표 행(<div class="table-row">)만 끊어서 그 안을 본다.
    """
    _, html = client.get("/inventory")
    for row in re.findall(r'<div class="table-row"[^>]*>(.*?)(?=<div class="table-row"|</div>\s*</div>)',
                          html, re.S):
        if name in row:
            return row
    return ""


def main():
    client, _, location = tenant_login(TA_EMAIL, TA_PASS)
    if "/login" in (location or "") or "/enter" in (location or ""):
        sys.exit("로그인 실패 — 서버와 시드 데이터를 확인하세요.")

    paths = leaf_paths(client)
    if not paths:
        sys.exit("등록 폼에서 분류 경로를 찾지 못했습니다.")
    real = next((p for p in paths if p.count("/") >= 1), paths[0])
    short = real.split("/")[-1].strip()
    print(f"쓸 분류: 전체={real!r} 짧은이름={short!r}\n")

    # 1) 없는 분류 — 거부되고 화면에 보여야 한다
    print("1) PC 등록에 없는 분류")
    name = f"유령-{abs(hash(real)) % 100000}"
    token = client.csrf("/inventory")
    status, loc, body = client.post("/inventory", [
        ("name", name), ("category", "실물 / 없는분류 / 더없음"), ("_csrf", token)])
    check("리다이렉트가 아니다(폼으로 되돌아온다)", status == 200, f"{status} -> {loc}")
    check("화면에 '없는 분류입니다' 가 보인다", "없는 분류입니다" in body)
    check("저장되지 않았다", name not in item_row(client, name))

    # 2) 짧은 이름 — 전체 경로로 저장
    print("\n2) PC 등록에 짧은 이름")
    short_name = f"짧은이름-{abs(hash(short)) % 100000}"
    token = client.csrf("/inventory")
    status, loc, _ = client.post("/inventory", [
        ("name", short_name), ("category", short), ("_csrf", token)])
    if check("등록 성공", ok(status, loc), f"{status} -> {loc}"):
        row = item_row(client, short_name)
        check("표 행을 찾았다", bool(row), "목록 표에서 그 자산을 못 찾음")
        check("전체 경로로 저장됐다", real.replace(" ", "") in row.replace(" ", ""),
              f"행에 보이는 분류: {re.sub(r'<[^>]+>', '', row)[:120]!r}")

    # 3) CSV 임포트 — 없는 분류 거부 + 모순 거부 + 정상 통과
    print("\n3) CSV 임포트")
    rows = [
        f"CSV유령-{abs(hash(real)) % 1000},,실물 / 없는분류,,,,,,",
        f"CSV모순-{abs(hash(real)) % 1000},SW,{real},,,,,,",
        f"CSV정상-{abs(hash(real)) % 1000},,{short},,,,,,",
    ]
    csv = "\n".join(rows) + "\n"
    token = client.csrf("/inventory/import")
    status, loc, body = client.post("/inventory/import", [("csvText", csv), ("_csrf", token)])
    # 열 매핑 확인 화면이 먼저 나온다 — 그대로 확정한다.
    mapping = dict(re.findall(r'name="(map_[^"]+)"[^>]*>.*?<option value="(\d+)" selected', body, re.S))
    if not mapping:
        # selected 가 없으면 우리 형식 순서대로 매긴다.
        targets = re.findall(r'name="(map_[^"]+)"', body)
        mapping = {t: str(i) for i, t in enumerate(dict.fromkeys(targets))}
    fields = [("rawCsv", csv), ("hasHeader", "false"), ("_csrf", client.csrf("/inventory/import"))]
    fields += list(mapping.items())
    status, loc, result = client.post("/inventory/import/confirm", fields)
    check("임포트 결과 화면이 떴다", status == 200, f"{status} -> {loc}")
    check("없는 분류 행이 거부됐다", "없는 분류입니다" in result)
    check("모순 행이 거부됐다", "유형과 분류가 맞지 않습니다" in result)
    check("정상 행은 등록됐다", f"CSV정상-{abs(hash(real)) % 1000}" in item_row(
        client, f"CSV정상-{abs(hash(real)) % 1000}"))

    print(f"\n결과: {len(PASS)} 통과 / {len(FAIL)} 실패")
    for failure in FAIL:
        print(f"  - {failure}")
    sys.exit(1 if FAIL else 0)


if __name__ == "__main__":
    main()
