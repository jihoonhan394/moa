"""시딩된 자산 일부를 사람에게 배정하고, 관리번호를 기존 자산에 붙인다."""
import re, sys
sys.path.insert(0, str(__import__('pathlib').Path(__file__).parent))
import seed_tenant as seed

client, status, _ = seed.tenant_login(seed.TA_EMAIL, seed.TA_PASS)
print('로그인:', status)

_, users_html = client.get('/users')
user_ids = list(dict.fromkeys(re.findall(r'/users/([0-9a-f-]{36})/edit', users_html)))
print('사용자:', len(user_ids))

_, inv_html = client.get('/inventory')
item_ids = list(dict.fromkeys(re.findall(r'/inventory/([0-9a-f-]{36})', inv_html)))
print('자산:', len(item_ids))

assigned = 0
for n, item in enumerate(item_ids[:45]):
    user = user_ids[n % len(user_ids)]
    token = client.csrf('/inventory')
    status, location, _ = client.post(f'/inventory/{item}/assign', [('userId', user), ('_csrf', token)])
    if seed.ok(status, location):
        assigned += 1
print('배정:', assigned)
