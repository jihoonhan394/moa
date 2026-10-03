"""테스트 데이터를 한 번에 채운다.

사용법 (서버가 8081 에 떠 있어야 한다):
    python scripts/testdata/run_all.py

서버는 아래처럼 띄운다 — 플랫폼 관리자와 샘플 데이터가 필요하다.
    set MOA_BOOTSTRAP_ADMIN_PASSWORD=BootstrapAdmin-2026!
    set MOA_SAMPLE_DATA_ENABLED=true
    set MOA_SAMPLE_USER_PASSWORD=LocalDemo2026!
    set MOA_VAULT_MASTER_KEY=<64 hex>
    gradlew.bat bootRun --args="--spring.profiles.active=local --server.port=8081"

local 프로파일은 H2 메모리라 **서버를 다시 띄우면 데이터가 사라진다.** 그때는 이 스크립트를
다시 돌리면 된다.
"""
import pathlib
import runpy
import sys

HERE = pathlib.Path(__file__).parent
sys.path.insert(0, str(HERE))

for step in ('seed_tenant', 'assign_items', 'attach_tags', 'seed_domains'):
    print(f'\n━━━ {step} ━━━')
    runpy.run_path(str(HERE / f'{step}.py'), run_name='__main__')
