"""작은 SVG → Android VectorDrawable 변환기(염소 아이콘에 쓰인 요소만: g transform, path, circle, ellipse, rect, line, radialGradient)."""
import re, sys, xml.etree.ElementTree as ET
NS='{http://www.w3.org/2000/svg}'
def num(v): return float(v)
def color(c, op=1.0):
    if c is None or c=='none': return None
    c=c.strip()
    if c.startswith('#'):
        h=c[1:]
        if len(h)==3: h=''.join(x*2 for x in h)
        a=int(round(op*255))
        return '#%02X%s'%(a,h.upper())
    return c
def circ(cx,cy,rx,ry):
    return f'M{cx-rx},{cy}a{rx},{ry} 0 1,0 {2*rx},0a{rx},{ry} 0 1,0 {-2*rx},0Z'
def rect(x,y,w,h,r):
    r=min(r,w/2,h/2)
    if r<=0: return f'M{x},{y}h{w}v{h}h{-w}Z'
    return (f'M{x+r},{y}h{w-2*r}a{r},{r} 0 0 1 {r},{r}v{h-2*r}a{r},{r} 0 0 1 {-r},{r}'
            f'h{-(w-2*r)}a{r},{r} 0 0 1 {-r},{-r}v{-(h-2*r)}a{r},{r} 0 0 1 {r},{-r}Z')
class Conv:
    def __init__(s, root):
        s.grads={}
        for g in root.iter(NS+'radialGradient'):
            stops=[(float(st.get('offset')), st.get('stop-color')) for st in g.findall(NS+'stop')]
            s.grads[g.get('id')]=dict(cx=float(g.get('cx','0.5')),cy=float(g.get('cy','0.5')),r=float(g.get('r','0.5')),stops=stops,kind='radial')
        for g in root.iter(NS+'linearGradient'):
            stops=[(float(st.get('offset')), st.get('stop-color')) for st in g.findall(NS+'stop')]
            s.grads[g.get('id')]=dict(x1=float(g.get('x1','0')),y1=float(g.get('y1','0')),x2=float(g.get('x2','1')),y2=float(g.get('y2','0')),stops=stops,kind='linear')
        s.out=[]
    def bbox(s,d):
        nums=[float(x) for x in re.findall(r'-?\d+\.?\d*',d)]
        xs=nums[0::2]; ys=nums[1::2]
        return min(xs),min(ys),max(xs),max(ys)
    def shape(s, e, ind):
        tag=e.tag.replace(NS,'')
        a=e.attrib
        if tag=='path': d=a['d']
        elif tag=='circle': r=num(a['r']); d=circ(num(a['cx']),num(a['cy']),r,r)
        elif tag=='ellipse': d=circ(num(a['cx']),num(a['cy']),num(a['rx']),num(a['ry']))
        elif tag=='rect': d=rect(num(a.get('x','0')),num(a.get('y','0')),num(a['width']),num(a['height']),num(a.get('rx','0')))
        elif tag=='line': d=f"M{a['x1']},{a['y1']}L{a['x2']},{a['y2']}"
        else: return
        op=float(a.get('opacity','1'))
        attrs=[f'android:pathData="{d}"']
        fill=a.get('fill', None if tag=='line' else '#000')
        inner=''
        if fill and fill.startswith('url('):
            gid=fill[5:-1]; g=s.grads[gid]
            x0,y0,x1,y1=s.bbox(d) if tag=='path' else (0,0,0,0)
            if tag!='path':
                x0,y0,x1,y1=s.bbox(d)
            w=x1-x0; h=y1-y0
            items=''.join(f'<item android:offset="{o}" android:color="{color(c)}"/>' for o,c in g['stops'])
            if g['kind']=='radial':
                gx=x0+g['cx']*w; gy=y0+g['cy']*h; gr=g['r']*max(w,h)
                inner=f'<aapt:attr name="android:fillColor"><gradient android:type="radial" android:centerX="{gx:.1f}" android:centerY="{gy:.1f}" android:gradientRadius="{gr:.1f}">{items}</gradient></aapt:attr>'
            else:
                inner=f'<aapt:attr name="android:fillColor"><gradient android:type="linear" android:startX="{x0+g["x1"]*w:.1f}" android:startY="{y0+g["y1"]*h:.1f}" android:endX="{x0+g["x2"]*w:.1f}" android:endY="{y0+g["y2"]*h:.1f}">{items}</gradient></aapt:attr>'
        elif fill and fill!='none':
            attrs.append(f'android:fillColor="{color(fill, op*float(a.get("fill-opacity","1")))}"')
        st=a.get('stroke')
        if st and st!='none':
            attrs.append(f'android:strokeColor="{color(st, op*float(a.get("stroke-opacity","1")))}"')
            attrs.append(f'android:strokeWidth="{a.get("stroke-width","1")}"')
            if a.get('stroke-linecap'): attrs.append(f'android:strokeLineCap="{a["stroke-linecap"]}"')
            if a.get('stroke-linejoin'): attrs.append(f'android:strokeLineJoin="{a["stroke-linejoin"]}"')
        if a.get('fill-rule')=='evenodd': attrs.append('android:fillType="evenOdd"')
        if inner: s.out.append(f'{ind}<path {" ".join(attrs)}>{inner}</path>')
        else: s.out.append(f'{ind}<path {" ".join(attrs)}/>')
    def group(s, e, ind):
        for c in e:
            tag=c.tag.replace(NS,'')
            if tag in ('defs','clipPath','radialGradient','linearGradient'): continue
            if tag=='g':
                t=c.get('transform','')
                ga=[]
                m=re.match(r'matrix\(-1 0 0 1 (\S+) 0\)',t)
                if m: ga=['android:scaleX="-1"',f'android:pivotX="{float(m.group(1))/2}"']
                m2=re.match(r'translate\((\S+) (\S+)\) scale\((\S+)\)',t)
                if m2: ga=[f'android:translateX="{m2.group(1)}"',f'android:translateY="{m2.group(2)}"',f'android:scaleX="{m2.group(3)}"',f'android:scaleY="{m2.group(3)}"']
                s.out.append(f'{ind}<group {" ".join(ga)}>'); s.group(c, ind+'    '); s.out.append(f'{ind}</group>')
            else: s.shape(c, ind)
def convert(svg_text, size_dp=108):
    root=ET.fromstring(svg_text)
    vb=root.get('viewBox').split()
    c=Conv(root); c.group(root,'    ')
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- docs/branding/goat에서 만든 SVG를 변환한 것(svg2vd). 손으로 고치지 말고 SVG를 고친 뒤 다시 변환한다. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{size_dp}dp" android:height="{size_dp}dp" android:viewportWidth="{vb[2]}" android:viewportHeight="{vb[3]}">\n'
            +'\n'.join(c.out)+'\n</vector>\n')
