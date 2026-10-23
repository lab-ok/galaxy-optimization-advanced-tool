"""1번 시안(클래식 귀염)으로 앱 아이콘 벡터 드로어블을 만든다.
    python3 docs/branding/goat/make_icons.py
generate.py의 염소 모양을 그대로 쓰고, svg2vd.py로 Android VectorDrawable로 바꿔 app/src/main/res/drawable에 쓴다.
- ic_launcher_fg: 적응형 아이콘 전경(안전 영역 지름 66dp 안)
- ic_launcher_mono: 테마 아이콘(단색, 눈·코는 구멍)
- ic_app_logo: 앱 안 로고(검은 둥근 타일 + 염소)
- ic_goat: 염소 얼굴만(사이드바 머리)
- ic_goat_squint·ic_goat_cry·ic_goat_angry: 누를 때 표정(찡그림·울음·화남)
- ic_stat_goat: 알림 작은 아이콘(단색 염소, 24dp를 거의 채운다, 시안 1 '동글 머리')
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


def drop(x, y, r, c='#7CC8FF'):
    """눈물 방울(위가 뾰족한 물방울)."""
    return (f'<path d="M{x} {y-r*1.9} Q{x+r} {y-r*0.4} {x+r} {y+0.2*r} A{r} {r} 0 1 1 {x-r} {y+0.2*r} '
            f'Q{x-r} {y-r*0.4} {x} {y-r*1.9} Z" fill="{c}"/>')

out = {
    'ic_launcher_fg.xml': (svg(scale(body, 0.84, -4)), 108),
    'ic_app_logo.xml': (svg(tile('#111111'), scale(body, 1.04, -2)), 72),
    'ic_goat.xml': (svg(scale(body, 1.0, 0)), 72),
    # 누를 때 표정: 찡그림 / 많이 누르면 울음 / 더 누르면 화남(볼이 빨개짐)
    'ic_goat_squint.xml': (svg(scale(goat(eye='squint', mouth='frown'), 1.0, 0)), 72),
    # 울음(그렁그렁): 눈물이 고인 큰 눈 + 걱정 눈썹 + 떨리는 입 + 볼에 맺힌 눈물 방울 + 처진 귀
    'ic_goat_cry.xml': (svg(scale(goat(eye='teary', mouth='quiver', blush='#FF8FA3', blush_op=0.8, ear_rot=-24, extra=drop(184, 330, 11) + drop(330, 342, 9)), 1.0, 0)), 72),
    'ic_goat_angry.xml': (svg(scale(goat(eye='angry', mouth='frown', blush='#FF4D4D', blush_op=0.85), 1.0, 0)), 72),
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
# 상단바(알림) 아이콘: 시안 1 '동글 머리'(2026-10-23 소장님 선택) — 더 넓고 둥근 머리, 짧은 뿔, 둥근 귀, 수염 없음
stub_horn = 'M214 186 C206 150 188 128 166 124 C156 124 154 134 160 140 C172 152 178 170 182 190 C186 202 202 204 214 186 Z'
round_ear = 'M166 254 C140 238 104 242 90 266 C86 276 96 288 116 290 C140 292 162 286 176 276 Z'
wide_head = 'M256 176 C336 176 382 222 380 280 C378 340 330 380 256 380 C182 380 134 340 132 280 C130 222 176 176 256 176 Z'
stat = (f'<path d="{stub_horn}" fill="#fff"/><g {mir}><path d="{stub_horn}" fill="#fff"/></g>'
        f'<path d="{round_ear}" fill="#fff"/><g {mir}><path d="{round_ear}" fill="#fff"/></g>'
        f'<path d="{wide_head} {circ(210, 284, 27)} {circ(302, 284, 27)} M243 316 Q256 308 269 316 Q264 330 256 332 Q248 330 243 316 Z" fill="#fff" fill-rule="evenodd"/>')
out['ic_stat_goat.xml'] = (svg(scale(stat, 1.45, -9)), 24)

for name, (s, dp) in out.items():
    with open(os.path.join(res, name), 'w', encoding='utf-8') as f:
        f.write(convert(s, dp))
    print('wrote', name)
