"""흰 염소 옆모습 실루엣(시안 6 '걷는 염소', 2026-10-31 소장님 선택)으로 세 번째 염소 아이콘을 만든다.
    python3 docs/branding/goat/silhouette.py
몸·머리 윤곽은 점들을 Catmull-Rom 곡선으로 잇고, 다리·뿔·수염·꼬리는 중심선과 굵기로 띠를 만든다. 왼쪽을 본다.
시안 원본: ~/claude_workspace/develop/goat/branding/silhouette_drafts/ (512 칸, 바탕 #2C2430 둥근 사각형)
- ic_launcher_bg_silhouette: 적응형 아이콘 바탕(#2C2430)
- ic_launcher_fg_silhouette: 적응형 아이콘 전경. 시안의 512 칸을 보이는 72dp 영역에 그대로 옮긴다
- ic_launcher_mono_silhouette: 테마(단색) 아이콘. 먼 쪽 다리는 반투명
- ic_goat_silhouette: 앱 안 로고(사이드바·앱 정보의 어두운 로고 상자 위 흰 염소)
"""
import math, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from svg2vd import convert

BG = '#2C2430'
WHITE = '#FFFFFF'
FAR = '#D8D2DE'  # 먼 쪽 다리: 앞 다리와 겹쳐도 구분되게 조금 어둡게
FAR_MONO = ('#FFFFFF', 0.55)  # 단색 아이콘의 먼 쪽 다리: 반투명


def paint(fill):
    """색 또는 (색, 불투명도)."""
    if isinstance(fill, tuple):
        return f'fill="{fill[0]}" fill-opacity="{fill[1]}"'
    return f'fill="{fill}"'


class Goat:
    """점 좌표에 [sc]배·[dx, dy] 이동을 바로 적용해 path를 만든다(svg2vd가 겹친 변환을 다루지 않아서)."""

    def __init__(self, sc=1.0, dx=0.0, dy=0.0):
        self.sc, self.dx, self.dy = sc, dx, dy

    def p(self, x, y):
        return (x * self.sc + self.dx, y * self.sc + self.dy)

    def cr(self, pts):
        pts = [self.p(x, y) for x, y in pts]
        n = len(pts)
        d = f'M{pts[0][0]:.2f},{pts[0][1]:.2f}'
        for i in range(n):
            p0, p1, p2, p3 = pts[(i - 1) % n], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
            c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
            c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
            d += f' C{c1[0]:.2f},{c1[1]:.2f} {c2[0]:.2f},{c2[1]:.2f} {p2[0]:.2f},{p2[1]:.2f}'
        return d + ' Z'

    def shape(self, pts, fill=WHITE):
        return f'<path d="{self.cr(pts)}" {paint(fill)}/>'

    def limb(self, cl, ws, fill=WHITE):
        left, right = [], []
        for i, (x, y) in enumerate(cl):
            a, b = cl[max(0, i - 1)], cl[min(len(cl) - 1, i + 1)]
            dx, dy = b[0] - a[0], b[1] - a[1]
            ln = math.hypot(dx, dy) or 1
            nx, ny, w = -dy / ln, dx / ln, ws[i] / 2
            left.append((x + nx * w, y + ny * w))
            right.append((x - nx * w, y - ny * w))
        return self.shape(left + right[::-1], fill)

    def hoof(self, x, y, fill=WHITE, w=13):
        q = [self.p(*t) for t in ((x + w * 0.5, y - 10), (x - w * 0.45, y - 10), (x - w * 0.95, y), (x + w * 0.55, y))]
        return '<path d="M' + ' L'.join(f'{a:.2f},{b:.2f}' for a, b in q) + f' Z" {paint(fill)}/>'

    def foreleg(self, x, ground, step, fill=WHITE):
        s = step
        cl = [(x, 262), (x - 1 + s * 0.15, 312), (x + s * 0.35, 345), (x + s * 0.6, 375), (x + s * 0.85, 400), (x - 3 + s, ground - 6)]
        return self.limb(cl, [34, 21, 17, 12, 11, 11], fill) + self.hoof(x - 3 + s, ground, fill)

    def hindleg(self, x, ground, step, fill=WHITE):
        s = step
        cl = [(x, 232), (x + 10, 282), (x + 22 + s * 0.3, 326), (x + 16 + s * 0.6, 362), (x + 11 + s * 0.85, 398), (x + 8 + s, ground - 6)]
        return self.limb(cl, [60, 36, 17, 12, 11, 11], fill) + self.hoof(x + 8 + s, ground, fill)

    def torso(self):
        return self.shape([(166, 124), (186, 146), (206, 170), (230, 182), (270, 187), (320, 185), (352, 181), (380, 179), (400, 186),
                           (410, 204), (414, 232), (406, 258), (390, 276), (360, 284), (310, 293), (262, 296), (224, 292), (204, 284),
                           (186, 268), (176, 244), (170, 220), (162, 196), (152, 176), (150, 150)])

    def head(self):
        g = [self.shape([(162, 112), (146, 116), (126, 128), (108, 146), (94, 164), (86, 178), (86, 188), (92, 194), (104, 198),
                         (126, 198), (146, 194), (162, 184), (172, 166), (174, 140)])]
        g.append(self.shape([(166, 134), (186, 130), (208, 134), (216, 140), (200, 148), (180, 148), (166, 146)]))  # 귀
        g.append(self.limb([(104, 196), (106, 214), (110, 232), (114, 244)], [15, 13, 8, 3]))  # 수염
        g.append(self.limb([(150, 118), (154, 98), (166, 82), (184, 74), (202, 78)], [15, 12, 10, 7, 3]))  # 뒤로 휜 뿔
        return ''.join(g)

    def walking(self, far=FAR):
        """시안 6: 먼 쪽 다리(앞 뒤로, 조금 어둡게) → 몸·꼬리 → 가까운 쪽 다리(반대로 내딛음) → 목·머리."""
        return (self.foreleg(226, 416, 24, far) + self.hindleg(366, 416, -26, far) + self.torso()
                + self.limb([(402, 190), (410, 178), (416, 164)], [13, 10, 5])
                + self.foreleg(204, 420, -26) + self.hindleg(388, 420, 22) + self.head())


def svg(body, size=512):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}">{body}</svg>'


# 시안의 배치: 512 칸에서 가운데 기준 0.96배, (4, -6) 이동
DRAFT = dict(sc=0.96, dx=256 - 256 * 0.96 + 4, dy=256 - 256 * 0.96 - 6)


def draft_goat(k=1.0, ox=0.0, oy=0.0):
    """시안 배치에 k배·이동을 더한 Goat."""
    return Goat(DRAFT['sc'] * k, DRAFT['dx'] * k + ox, DRAFT['dy'] * k + oy)


if __name__ == '__main__':
    res = os.path.join(HERE, '..', '..', '..', 'app', 'src', 'main', 'res', 'drawable')
    # 적응형 아이콘: 108 칸 가운데 72dp가 보이는 영역이므로 시안의 512 칸을 72/512배로 줄여 18만큼 옮긴다
    k, o = 72 / 512, 18
    fg = draft_goat(k, o, o)
    out = {
        'ic_launcher_bg_silhouette.xml': (svg(f'<rect x="0" y="0" width="108" height="108" fill="{BG}"/>', 108), 108),
        'ic_launcher_fg_silhouette.xml': (svg(fg.walking(), 108), 108),
        'ic_launcher_mono_silhouette.xml': (svg(fg.walking(far=FAR_MONO), 108), 108),
        # 앱 안 로고: 염소(대략 x 86~420, y 74~420)를 72 칸 가운데에 넉넉히 채운다
        'ic_goat_silhouette.xml': (svg(Goat(72 / 380, -86 * 72 / 380 + 72 * 0.03, -60 * 72 / 380).walking(), 72), 72),
    }
    for name, (s, dp) in out.items():
        with open(os.path.join(res, name), 'w', encoding='utf-8') as f:
            f.write(convert(s, dp))
        print('wrote', name)
