import cairosvg, os
os.chdir(os.path.dirname(os.path.abspath(__file__)))
MIR='transform="matrix(-1 0 0 1 512 0)"'

def tile(bg, rx=112):
    if bg.startswith('grad:'):
        a,b=bg[5:].split(',')
        return f'<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{a}"/><stop offset="1" stop-color="{b}"/></linearGradient></defs><rect width="512" height="512" rx="{rx}" fill="url(#bg)"/>'
    return f'<rect width="512" height="512" rx="{rx}" fill="{bg}"/>'

def horn(c, ridge, style='curl'):
    if style=='curl':
        h=f'<path d="M216 194 C204 152 182 122 152 112 C146 110 142 116 146 121 C162 138 170 160 176 184 C180 198 188 206 200 210 Z" fill="{c}"/>'
        r=''.join(f'<path d="{d}" stroke="{ridge}" stroke-width="5" stroke-linecap="round" fill="none"/>' for d in ['M171 150 Q181 146 189 152','M178 170 Q190 166 197 172','M183 189 Q195 186 202 192'])
        return h+r
    if style=='arrow':  # resize-arrow horns
        return (f'<path d="M206 196 C196 160 176 136 150 126" stroke="{c}" stroke-width="18" stroke-linecap="round" fill="none"/>'
                f'<path d="M138 112 L172 116 L150 142 Z" fill="{c}" stroke="{c}" stroke-width="8" stroke-linejoin="round"/>')
    return ''

def ear(fur, inner):
    return (f'<path d="M168 250 C138 232 104 236 82 258 C80 262 82 266 86 268 C112 288 146 290 174 276 Z" fill="{fur}"/>'
            f'<path d="M160 256 C138 246 116 248 102 262 C122 276 146 278 166 270 Z" fill="{inner}"/>')

HEAD='M256 170 C320 170 362 212 360 270 C358 330 316 374 256 376 C196 374 154 330 152 270 C150 212 192 170 256 170 Z'

def goat(fur='#FFF6E9', shade='#F1DFC6', muzzle='#FFFFFF', horn_c='#C79A6B', ridge='#A87B4F', ear_in='#FFB8B8',
         eye='round', eye_c='#2B2024', blush='#FF9DA7', blush_op=0.55, outline=None, ow=10, mouth='smile',
         horn_style='curl', beard=True, tuft=True, half=None, extra=''):
    o=f' stroke="{outline}" stroke-width="{ow}" stroke-linejoin="round"' if outline else ''
    g=[]
    g.append('<defs><radialGradient id="head" cx="0.42" cy="0.32" r="0.8"><stop offset="0" stop-color="%s"/><stop offset="1" stop-color="%s"/></radialGradient></defs>'%(fur,shade))
    if outline:  # sticker outline behind everything
        g.append(f'<g stroke="{outline}" stroke-width="{ow*2}" stroke-linejoin="round" fill="{outline}">'
                 f'{horn("x","x",horn_style).replace("fill=\"x\"","").replace("stroke=\"x\"","")}'
                 f'<g {MIR}>{horn("x","x",horn_style).replace("fill=\"x\"","").replace("stroke=\"x\"","")}</g>'
                 f'<path d="M168 250 C138 232 104 236 82 258 C80 262 82 266 86 268 C112 288 146 290 174 276 Z"/><g {MIR}><path d="M168 250 C138 232 104 236 82 258 C80 262 82 266 86 268 C112 288 146 290 174 276 Z"/></g>'
                 f'<path d="{HEAD}"/><path d="M240 368 Q256 414 272 368 Z"/></g>')
    g.append(horn(horn_c,ridge,horn_style)); g.append(f'<g {MIR}>{horn(horn_c,ridge,horn_style)}</g>')
    g.append(ear(fur,ear_in)); g.append(f'<g {MIR}>{ear(fur,ear_in)}</g>')
    if beard: g.append(f'<path d="M240 366 Q248 382 252 404 Q256 412 260 404 Q264 382 272 366 Z" fill="{shade}"/>')
    g.append(f'<path d="{HEAD}" fill="url(#head)"/>')
    if half:  # fold hinge: darken right half
        g.append(f'<clipPath id="hc"><path d="{HEAD}"/></clipPath><rect x="256" y="160" width="120" height="230" fill="{half}" clip-path="url(#hc)"/>'
                 f'<line x1="256" y1="172" x2="256" y2="374" stroke="#8C7A66" stroke-opacity="0.55" stroke-width="4" stroke-linecap="round"/>')
    if tuft: g.append(f'<path d="M232 182 Q236 156 252 164 Q258 146 272 160 Q284 158 282 176 Q262 172 246 188 Z" fill="{fur}"/>')
    g.append(f'<ellipse cx="256" cy="330" rx="58" ry="42" fill="{muzzle}" opacity="0.95"/>')
    g.append('<path d="M243 316 Q256 308 269 316 Q264 330 256 332 Q248 330 243 316 Z" fill="#F08A9A"/>')
    if mouth=='smile': g.append('<path d="M240 344 Q248 354 256 346 Q264 354 272 344" stroke="%s" stroke-width="5" fill="none" stroke-linecap="round"/>'%eye_c)
    if mouth=='frown': g.append('<path d="M238 352 Q256 336 274 352" stroke="%s" stroke-width="6" fill="none" stroke-linecap="round"/>'%eye_c)
    if mouth=='wobble': g.append('<path d="M244 352 Q250 344 256 350 Q262 344 268 352" stroke="%s" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>'%eye_c)
    if mouth=='tongue': g.append('<path d="M240 344 Q256 360 272 344" stroke="%s" stroke-width="5" fill="none" stroke-linecap="round"/><path d="M250 352 Q256 368 262 352 Z" fill="#FF6F86"/>'%eye_c)
    for cx in (212,300):
        if eye=='round':
            g.append(f'<circle cx="{cx}" cy="282" r="25" fill="{eye_c}"/><circle cx="{cx-8}" cy="272" r="8.5" fill="#fff"/><circle cx="{cx+8}" cy="292" r="4" fill="#fff"/>')
        elif eye=='goat':
            g.append(f'<ellipse cx="{cx}" cy="282" rx="25" ry="22" fill="#F2B544"/><rect x="{cx-17}" y="276" width="34" height="12" rx="6" fill="#1D1416"/><circle cx="{cx-9}" cy="272" r="5" fill="#fff"/>')
        elif eye=='wink' and cx==212:
            g.append(f'<path d="M{cx-18} 280 Q{cx} 266 {cx+18} 280" stroke="{eye_c}" stroke-width="7" fill="none" stroke-linecap="round"/>')
        elif eye=='wink':
            g.append(f'<circle cx="{cx}" cy="282" r="25" fill="{eye_c}"/><circle cx="{cx-8}" cy="272" r="8.5" fill="#fff"/><circle cx="{cx+8}" cy="292" r="4" fill="#fff"/>')
        elif eye=='squint':  # 찡그림: 왼쪽 눈 >, 오른쪽 눈 <
            d = 1 if cx==212 else -1
            g.append(f'<path d="M{cx-14*d} 268 L{cx+12*d} 282 L{cx-14*d} 296" stroke="{eye_c}" stroke-width="8" fill="none" stroke-linecap="round" stroke-linejoin="round"/>')
        elif eye=='cry':  # 울먹: 눈물이 그렁그렁 고인 큰 눈(반짝임 둘) + 아래 눈꺼풀에 고인 눈물 + 흘러내리는 눈물 방울
            g.append(f'<circle cx="{cx}" cy="284" r="26" fill="{eye_c}"/>'
                     f'<path d="M{cx-24} 292 Q{cx} 316 {cx+24} 292 Q{cx} 302 {cx-24} 292 Z" fill="#8FD3FF" opacity="0.9"/>'
                     f'<circle cx="{cx-9}" cy="273" r="10" fill="#fff"/><circle cx="{cx+10}" cy="290" r="5" fill="#fff"/><circle cx="{cx+3}" cy="268" r="3" fill="#fff"/>'
                     f'<path d="M{cx+(16 if cx==300 else -16)} 302 Q{cx+(8 if cx==300 else -8)} 322 {cx+(16 if cx==300 else -16)} 332 Q{cx+(24 if cx==300 else -24)} 322 {cx+(16 if cx==300 else -16)} 302 Z" fill="#8FD3FF"/>')
        elif eye=='angry':  # 화남: 작은 눈 + 안쪽으로 내려간 눈썹
            d = 1 if cx==212 else -1
            g.append(f'<circle cx="{cx}" cy="286" r="17" fill="{eye_c}"/><circle cx="{cx-5}" cy="280" r="5" fill="#fff"/>'
                     f'<path d="M{cx-24*d} 252 L{cx+20*d} 266" stroke="{eye_c}" stroke-width="9" stroke-linecap="round"/>')
        elif eye=='line':
            g.append(f'<rect x="{cx-18}" y="273" width="36" height="11" rx="5.5" fill="{eye_c}"/>')
    if blush: g.append(f'<ellipse cx="186" cy="314" rx="19" ry="10" fill="{blush}" opacity="{blush_op}"/><ellipse cx="326" cy="314" rx="19" ry="10" fill="{blush}" opacity="{blush_op}"/>')
    g.append(extra)
    return ''.join(g)

def svg(*parts): return '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">'+''.join(parts)+'</svg>'
def scale(body, k=0.9, dy=0):  # shrink toward center (safe zone)
    return f'<g transform="translate({256*(1-k)} {256*(1-k)+dy}) scale({k})">{body}</g>'

V={}
V['01 클래식 귀염']=svg(tile('#111111'), scale(goat(),1.04,-2))
V['02 파스텔 민트']=svg(tile('grad:#CFF3E6,#9FDCC8'), scale(goat(fur='#FFFFFF',shade='#EEF1EA',horn_c='#F4B98C',ridge='#D99567'),1.04,-2))
V['03 잉크 단색(앱 톤)']=svg(tile('#111111'), scale(goat(fur='#FFFFFF',shade='#E6E6E6',muzzle='#F4F4F4',horn_c='#FFFFFF',ridge='#BDBDBD',ear_in='#D9D9D9',eye='line',eye_c='#111111',blush=None,beard=True),1.04,-2))
fold_peek=('<g>'+'<rect x="96" y="318" width="320" height="196" rx="26" fill="#2A2A2A"/><rect x="112" y="334" width="288" height="180" rx="14" fill="#F6F1E8"/>'
           '<line x1="256" y1="334" x2="256" y2="514" stroke="#111" stroke-opacity="0.25" stroke-width="5"/></g>')
paws='<ellipse cx="206" cy="326" rx="24" ry="16" fill="#FFF6E9"/><ellipse cx="306" cy="326" rx="24" ry="16" fill="#FFF6E9"/><path d="M198 322 v8 M208 322 v8 M298 322 v8 M308 322 v8" stroke="#E5CDB0" stroke-width="3" stroke-linecap="round"/>'
V['04 화면 위로 빼꼼']=svg(tile('#111111'),'<clipPath id="tc"><rect width="512" height="512" rx="112"/></clipPath><g clip-path="url(#tc)">', scale(goat(beard=False),0.98,-52), fold_peek, paws,'</g>')
V['05 접는 선 염소']=svg(tile('#111111'), scale(goat(half='#000000" fill-opacity="0.16'),1.04,-2))
V['06 크기 조절 뿔']=svg(tile('grad:#22303A,#111111'), scale(goat(horn_style='arrow',horn_c='#FFD27A',ridge='#E0A94A'),1.04,-2))
V['07 스티커']=svg(tile('grad:#BFE3FF,#8EC8F5'), scale(goat(outline='#FFFFFF',ow=12),0.96,2))
V['08 윙크 장난']=svg(tile('grad:#FFE59A,#FFC85A'), scale(goat(eye='wink',mouth='tongue'),1.04,-2))
V['09 염소 눈동자']=svg(tile('grad:#1E2A4A,#0E1426'), scale(goat(eye='goat',blush_op=0.35),1.04,-2))
stars='<path d="M402 120 l6 14 14 6 -14 6 -6 14 -6 -14 -14 -6 14 -6 Z" fill="#FFD27A"/><path d="M112 384 l4 9 9 4 -9 4 -4 9 -4 -9 -9 -4 9 -4 Z" fill="#FFD27A"/>'
badge='<circle cx="256" cy="262" r="184" fill="#1B1B1B" stroke="#FFD27A" stroke-width="10"/><circle cx="256" cy="262" r="164" fill="none" stroke="#FFD27A" stroke-opacity="0.35" stroke-width="3" stroke-dasharray="3 10" stroke-linecap="round"/>'
V['10 금빛 배지']=svg(tile('#0B0B0B'), badge, scale(goat(horn_c='#FFD27A',ridge='#D9A441'),0.86,6), stars)

for name,s in V.items():
    fn=name.split()[0]
    open(f'goat_{fn}.svg','w').write(s)
    cairosvg.svg2png(bytestring=s.encode(), write_to=f'goat_{fn}.png', output_width=512, output_height=512)
print('\n'.join(V.keys()))
