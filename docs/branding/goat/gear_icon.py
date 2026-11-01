"""네 번째 염소 아이콘 '톱니'(2026-11-01 소장님 선택, 설정 톱니 시안 9번 '애플+삼성 · 고운 링 + 흰 판').
    python3 docs/branding/goat/gear_icon.py
어두운 그라데이션 바탕 위에 고운 이의 은색 톱니 링, 그 안의 흰 원판, 원판 위의 검은 염소(1.31.0 실루엣과 같은 곡선).
시안 원본: ~/claude_workspace/develop/goat/branding/gear_drafts/ (gear3.py, set_09.svg, 512 칸)
- ic_launcher_bg_gear: 적응형 아이콘 바탕(#4A4A4D → #1B1B1D 세로 그라데이션)
- ic_launcher_fg_gear: 적응형 아이콘 전경. 시안의 512 칸을 보이는 72dp 영역에 그대로 옮긴다(톱니 바깥 지름 약 60dp, 안전 영역 66dp 안)
- ic_launcher_mono_gear: 테마(단색) 아이콘. 톱니 링과 염소만(흰 원판은 빼서 염소가 보이게)
- ic_goat_gear: 앱 안 로고(사이드바·앱 정보의 로고 상자 안)
"""
import math, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from svg2vd import convert
from silhouette import Goat

GRADS = ('<defs>'
         '<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#4A4A4D"/><stop offset="1" stop-color="#1B1B1D"/></linearGradient>'
         '<linearGradient id="steel" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#F4F4F6"/><stop offset="1" stop-color="#B9B9BE"/></linearGradient>'
         '</defs>')
GOAT = '#1C1C1E'
GOAT_FAR = '#4A4A4E'


def circ(cx, cy, r):
    return f'M{cx + r:.2f},{cy:.2f} A{r:.2f},{r:.2f} 0 1,1 {cx - r:.2f},{cy:.2f} A{r:.2f},{r:.2f} 0 1,1 {cx + r:.2f},{cy:.2f} Z'


def fine_teeth(cx, cy, r_root, r_tip, z, tip=0.42, base=0.62):
    """고운 이(사다리꼴, 끝을 살짝 깎음). 시안 gear3.py와 같은 식."""
    pts = []
    step = 2 * math.pi / z
    for k in range(z):
        c = k * step
        hb, ht = step * base / 2, step * tip / 2
        for a, r in ((c - step / 2, r_root), (c - hb, r_root), (c - ht, r_tip), (c + ht, r_tip), (c + hb, r_root)):
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return 'M' + ' L'.join(f'{x:.2f},{y:.2f}' for x, y in pts) + ' Z'


def art(k, ox, oy, disk=True, goat=GOAT, far=GOAT_FAR, ring='url(#steel)'):
    """시안 9번(512 칸)을 k배·이동해 그린다. 염소는 시안처럼 0.5배로 (254, 262)가 가운데."""
    T = lambda v: v * k
    cx, cy = ox + T(256), oy + T(256)
    s = f'<path d="{fine_teeth(cx, cy, T(196), T(214), 44)} {circ(cx, cy, T(172))}" fill="{ring}" fill-rule="evenodd"/>'
    if disk:
        s += f'<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{T(158):.2f}" fill="#FFFFFF"/>'
    g = 0.5 * k
    s += Goat(g, ox + T(254) - 250 * g, oy + T(262) - 245 * g).walking().replace('#FFFFFF', goat).replace('#D8D2DE', far)
    return s


def svg(body, size):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}">{GRADS}{body}</svg>'


if __name__ == '__main__':
    res = os.path.join(HERE, '..', '..', '..', 'app', 'src', 'main', 'res', 'drawable')
    k, o = 72 / 512, 18
    out = {
        'ic_launcher_bg_gear.xml': (svg('<rect x="0" y="0" width="108" height="108" fill="url(#bg)"/>', 108), 108),
        'ic_launcher_fg_gear.xml': (svg(art(k, o, o), 108), 108),
        'ic_launcher_mono_gear.xml': (svg(art(k, o, o, disk=False, goat='#FFFFFF', far='#FFFFFF', ring='#FFFFFF'), 108), 108),
        # 앱 안 로고: 톱니 바깥(지름 428)이 72 칸을 거의 채우게
        'ic_goat_gear.xml': (svg(art(72 / 436, 36 - 256 * 72 / 436, 36 - 256 * 72 / 436), 72), 72),
    }
    for name, (s, dp) in out.items():
        with open(os.path.join(res, name), 'w', encoding='utf-8') as f:
            f.write(convert(s, dp))
        print('wrote', name)
