#!/usr/bin/env python3
"""HardcodedKoreanTest의 허용 목록(app/src/test/resources/korean_literal_baseline.txt)을 지금 코드로 다시 만든다.
문구를 리소스로 옮긴 뒤 실행해 허용 개수를 줄인다. 새 문구를 코드에 넣으려고 실행하지 않는다."""
import re, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / 'app/src/main/java'
OUT = ROOT / 'app/src/test/resources/korean_literal_baseline.txt'
EXEMPT = ['ui/about/Changelog.kt','ui/lab/LabScreen.kt','ui/lab/TentActivity.kt','data/lab/OttFlexTest.kt','data/lab/ShareProbe.kt','data/lab/ApiProbe.kt','data/log/DiagnosticReport.kt']
def count(src):
    code = re.sub(r'/\*.*?\*/', '', src, flags=re.S); code = re.sub(r'//[^\n]*', '', code)
    n = 0
    for line in code.split('\n'):
        if 'LogRepository' in line or re.search(r'\blog\.add\(|\bnote\(|Log\.[diwe]\(', line): continue
        n += sum(1 for m in re.findall(r'"((?:[^"\\]|\\.)*)"', line) if re.search('[가-힣]', m))
    return n
rows = []
for f in sorted(SRC.rglob('*.kt')):
    p = str(f.relative_to(SRC))
    if any(p.endswith(e) for e in EXEMPT): continue
    n = count(f.read_text())
    if n: rows.append(f'{p} {n}')
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text('# 아직 리소스로 옮기지 않은 한국어 문자열(파일 개수). tools/strings/korean_baseline.py로 만든다. 줄기만 해야 한다.\n' + '\n'.join(rows) + '\n')
print(len(rows), 'files', sum(int(r.rsplit(' ',1)[1]) for r in rows), 'literals')
