#!/usr/bin/env python3
"""Copy.kt의 문구 상수를 res/values/strings_common.xml로 옮기고, Copy.kt 상수를 리소스를 읽는 속성으로 바꾼다.

사용법: tools/strings/copy_to_res.py [--dry]
- 상수 이름 X → 리소스 이름 x(소문자). 다른 언어는 values-<언어>/strings_common.xml에 같은 이름으로 더한다.
- Copy.X를 쓰는 코드는 그대로 둔다(Copy.X가 리소스를 읽는다).
"""
import re, sys, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[2]
COPY = ROOT / 'app/src/main/java/com/labok/goat/ui/text/Copy.kt'
OUT = ROOT / 'app/src/main/res/values/strings_common.xml'
EXISTING = set(re.findall(r'name="(\w+)"', (ROOT / 'app/src/main/res/values/strings.xml').read_text()))

def kt_unescape(s):
    return re.sub(r'\\u([0-9a-fA-F]{4})', lambda m: chr(int(m.group(1), 16)), s).replace('\\"', '"').replace('\\\\', '\\').replace('\\n', '\n')

def xml_escape(s):
    s = s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    s = s.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if s.startswith('@') or s.startswith('?'): s = '\\' + s
    return s

src = COPY.read_text()
pat = re.compile(r'^(\s*)const val (\w+) = "((?:[^"\\]|\\.)*)"\s*$', re.M)
items = pat.findall(src)
lines = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- 공통 문구(예전 Copy.kt 상수). 문구는 여기서 고친다. 이름은 Copy.kt의 속성 이름을 소문자로 쓴 것이다. -->', '<resources>']
KEYWORDS = set('abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null'.split())
def res_name(n):
    r = n.lower()
    return r + '_label' if r in KEYWORDS else r
clash = [n for _, n, _ in items if res_name(n) in EXISTING]
assert not clash, clash
for _, name, val in items:
    v = kt_unescape(val)
    fmt = ' formatted="false"' if '%' in v else ''
    lines.append(f'    <string name="{res_name(name)}"{fmt}>{xml_escape(v)}</string>')
lines.append('</resources>')
if '--dry' in sys.argv:
    print(len(items), 'strings'); sys.exit(0)
OUT.write_text('\n'.join(lines) + '\n')
new = pat.sub(lambda m: f'{m.group(1)}val {m.group(2)}: String get() = s(R.string.{res_name(m.group(2))})', src)
new = new.replace('object Copy {', 'object Copy {\n    /**\n     * 문구 읽기. 앱 시작 때 [App]이 리소스로 채운다(res/values/strings_common.xml, 다른 언어는 values-<언어>/).\n     * JVM 단위 테스트는 xml을 직접 읽는 도우미로 바꿔 끼운다.\n     */\n    var lookup: (Int) -> String = { error(\"Copy 문구를 읽기 전에 App이 lookup을 채워야 합니다\") }\n\n    private fun s(id: Int): String = lookup(id)\n', 1)
new = new.replace('import java.util.Locale\n', 'import com.labok.goat.R\nimport java.util.Locale\n', 1)
COPY.write_text(new)
print('moved', len(items))
