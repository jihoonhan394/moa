"""배정된 자산에 관리번호를 붙인다(모바일 '기존 자산에 붙이기' 경로를 그대로 쓴다)."""
import re, sys
sys.path.insert(0, str(__import__('pathlib').Path(__file__).parent))
import seed_tenant as seed

client, status, _ = seed.tenant_login(seed.TA_EMAIL, seed.TA_PASS)
_, tags_html = client.get('/inventory/tags')
codes = re.findall(r'MOA-[0-9A-Z]{4}-[0-9A-Z]{4}', tags_html)
print('미부착 번호:', len(codes))

_, inv_html = client.get('/inventory')
item_ids = list(dict.fromkeys(re.findall(r'/inventory/([0-9a-f-]{36})', inv_html)))
print('자산:', len(item_ids))

done = 0
for code, item in zip(codes, item_ids[:70]):
    token = client.csrf('/m/scan')
    status, location, body = client.post(f'/m/t/{code}/attach', [('itemId', item), ('_csrf', token)])
    if seed.ok(status, location):
        done += 1
    elif done == 0:
        print('  실패', status, location, body[:200])
print('부착:', done)
