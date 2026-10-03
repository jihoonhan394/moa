"""폼에 바인딩된 필드 중 오류를 표시할 자리가 없는 것을 찾는다."""
import glob
import os
import re

ROOT = 'src/main/resources/templates'
bound = []
gap = []
for path in glob.glob(os.path.join(ROOT, '**', '*.html'), recursive=True):
    text = open(path, encoding='utf-8', errors='ignore').read()
    if 'th:object' not in text:
        continue
    bound.append(path)
    fields = set(re.findall(r'th:field="\*\{([a-zA-Z]+)', text))
    errors = set(re.findall(r'th:errors="\*\{([a-zA-Z]+)', text))
    missing = sorted(fields - errors)
    if missing:
        rel = os.path.relpath(path, ROOT).replace('\\', '/')
        gap.append((rel, missing))

print(f'폼 바인딩(th:object) 템플릿: {len(bound)}개')
print(f'그중 오류 표시가 빠진 필드가 있는 것: {len(gap)}개')
for rel, missing in sorted(gap, key=lambda item: -len(item[1]))[:14]:
    print(f'  - {rel}: {len(missing)}개 {missing[:6]}')
