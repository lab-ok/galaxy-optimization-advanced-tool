"""1번 시안(클래식 귀염)으로 앱 아이콘 벡터 드로어블을 만든다.
    python3 docs/branding/goat/make_icons.py
generate.py의 염소 모양을 그대로 쓰고, svg2vd.py로 Android VectorDrawable로 바꿔 app/src/main/res/drawable에 쓴다.
- ic_launcher_fg: 적응형 아이콘 전경(안전 영역 지름 66dp 안)
- ic_launcher_mono: 테마 아이콘(단색, 눈·코는 구멍)
- ic_app_logo: 앱 안 로고(검은 둥근 타일 + 염소)
- ic_goat: 염소 얼굴만(사이드바 머리)
- ic_stat_goat: 알림 작은 아이콘(단색 염소, 24dp를 거의 채운다)
"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from svg2vd import convert

src = open(os.path.join(HERE, 'generate.py'), encoding='utf-8').read()
ns = {}
exec(src.split('V={}')[0].replace('os.chdir(os.path.dirname(os.path.abspath(__file__)))', ''), ns)
goat, svg, tile, scale, HEAD = ns['goat'], ns['svg'], ns['tile'], ns['scale'], ns['HEAD']
res = os.path.join(HERE, '..', '..', '..', 'app', 'src', 'main', 'res', 'drawable')

body = goat()
out = {
    'ic_launcher_fg.xml': (svg(scale(body, 0.84, -4)), 108),
    'ic_app_logo.xml': (svg(tile('#111111'), scale(body, 1.04, -2)), 72),
    'ic_goat.xml': (svg(scale(body, 1.0, 0)), 72),
}

def circ(cx, cy, r): return f'M{cx-r},{cy}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0Z'
ear = 'M168 250 C138 232 104 236 82 258 C80 262 82 266 86 268 C112 288 146 290 174 276 Z'
horn = 'M216 194 C204 152 182 122 152 112 C146 110 142 116 146 121 C162 138 170 160 176 184 C180 198 188 206 200 210 Z'
mir = 'transform="matrix(-1 0 0 1 512 0)"'
mono = (f'<path d="{horn}" fill="#fff"/><g {mir}><path d="{horn}" fill="#fff"/></g>'
        f'<path d="{ear}" fill="#fff"/><g {mir}><path d="{ear}" fill="#fff"/></g>'
        '<path d="M240 366 Q248 382 252 404 Q256 412 260 404 Q264 382 272 366 Z" fill="#fff"/>'
        f'<path d="{HEAD} {circ(212, 282, 25)} {circ(300, 282, 25)} M243 316 Q256 308 269 316 Q264 330 256 332 Q248 330 243 316 Z" fill="#fff" fill-rule="evenodd"/>')
out['ic_launcher_mono.xml'] = (svg(scale(mono, 0.84, -4)), 108)
out['ic_stat_goat.xml'] = (svg(scale(mono, 1.45, -9)), 24)

for name, (s, dp) in out.items():
    with open(os.path.join(res, name), 'w', encoding='utf-8') as f:
        f.write(convert(s, dp))
    print('wrote', name)
