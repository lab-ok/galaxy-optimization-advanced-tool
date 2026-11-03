package com.labok.goat.ui.lab

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * GOAT 패널 카드의 리퀴드 글래스 셰이더(11-02 소장님 "깃 검색해서 리퀴드 글래스 구현 긴빠이").
 *
 * 조사 보고서 `liquid_glass_research_261102.md`의 레시피를 GOAT에 맞게 옮겼다. 출처(허용 라이선스만):
 * - Kyant0/AndroidLiquidGlass(Apache-2.0): 둥근 사각형 SDF, 가장자리 띠 안에서만 샘플 위치를 안쪽으로 당기는 굴절
 * - QWEA0/Liquid-Glass-Android(MIT): 광원 쪽·반대쪽 두 lobe의 테두리 빛, 1px 실선 + 안쪽 glow, 되접힘 고리를 막는 제곱 프로파일과 상한
 * - skydoves/Cloudy(Apache-2.0): 모서리 이음매가 부드러운 법선
 * 모서리 거리 함수는 GOAT 초타원 모양([SuperellipseShape])에 맞춰 원 대신 N-노름으로 바꿨다.
 *
 * GOAT는 카드 뒤 배경(반사 필드)을 스스로 그리므로 실제 화면을 캡처할 필요가 없다. 반사 필드(96칸)와 근거리 띠(384칸)를
 * 패널 좌표계로 늘린 BitmapShader 두 개를 입력으로 받아, 카드 하나를 셰이더 한 번으로 그린다.
 * 기존의 가짜 굴절(옮긴 사본)·스크린 사본·안쪽 그늘 선·대각선 테두리 선·모서리 광택 호를 이것 하나로 바꾼다.
 */
@RequiresApi(33)
internal class GlassShader {
    val shader = RuntimeShader(AGSL)
    private val fieldMatrix = Matrix()
    private val nearMatrix = Matrix()
    private var fieldSrc: ImageBitmap? = null
    private var nearSrc: ImageBitmap? = null
    private var fieldShader: BitmapShader? = null
    private var nearShader: BitmapShader? = null
    private var frostSrc: ImageBitmap? = null
    private var frostShader: BitmapShader? = null
    private val frostMatrix = Matrix()
    private val empty: BitmapShader by lazy {
        BitmapShader(android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888), Shader.TileMode.DECAL, Shader.TileMode.DECAL)
    }

    /**
     * 이번 프레임의 입력을 넣는다. [pos]는 패널 안에서 카드의 왼쪽 위, [panelW]·[panelH]는 패널 크기(px).
     * [nearFrac]은 근거리 띠가 덮는 패널 높이 비율. [reflect]는 반사 세기(프리셋·장면 밝기 반영), [press]는 눌림(0~1).
     */
    fun update(
        field: ImageBitmap?,
        near: ImageBitmap?,
        pos: androidx.compose.ui.geometry.Offset,
        panelW: Float,
        panelH: Float,
        nearFrac: Float,
        w: Float,
        h: Float,
        radius: Float,
        density: Float,
        reflect: Float,
        primary: Boolean,
        press: Float,
        frost: ImageBitmap? = null,
        sizeK: Float = 0f
    ) {
        shader.setFloatUniform("sizeK", sizeK)
        if (frost !== frostSrc) {
            frostSrc = frost
            frostShader = frost?.let { bmpShader(it) }
        }
        if (field !== fieldSrc) {
            fieldSrc = field
            fieldShader = field?.let { bmpShader(it) }
        }
        if (near !== nearSrc) {
            nearSrc = near
            nearShader = near?.let { bmpShader(it) }
        }
        // 비트맵 칸 → 패널 px로 늘리고, 카드 위치만큼 옮긴다(바닥과 같은 좌표계라 카드 경계에서 끊기지 않는다)
        field?.let { f ->
            fieldMatrix.setScale(panelW / f.width, panelH / f.height)
            fieldMatrix.postTranslate(-pos.x, -pos.y)
            fieldShader?.setLocalMatrix(fieldMatrix)
        }
        near?.let { n ->
            nearMatrix.setScale(panelW / n.width, panelH * nearFrac / n.height)
            nearMatrix.postTranslate(-pos.x, -pos.y)
            nearShader?.setLocalMatrix(nearMatrix)
        }
        frost?.let { f ->
            frostMatrix.setScale(panelW / f.width, panelH / f.height)
            frostMatrix.postTranslate(-pos.x, -pos.y)
            frostShader?.setLocalMatrix(frostMatrix)
        }
        shader.setInputShader("frost", frostShader ?: empty)
        shader.setFloatUniform("useFrost", if (frost != null) 1f else 0f)
        shader.setInputShader("field", fieldShader ?: empty)
        shader.setInputShader("near", nearShader ?: empty)
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("cornerRadius", radius)
        // 굴절 띠 폭과 최대 변위: 변위는 띠 폭의 절반 이하(그 이상이면 가장자리에 거울 고리가 생긴다, QWEA0 상한)
        val bevel = 22f * density
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("refraction", bevel * 0.5f * (1f - 0.35f * press))
        shader.setFloatUniform("dispersion", 0.10f)
        // 빛은 바로 위에서(11-03): 1.42.15의 왼쪽 위 광원은 어두운 장면에서 왼쪽 위 회색 호로 보였다(11-02 소장님 지적). 위에서 비추면
        // 위 가장자리 전체에 고르게 맺혀 '왼쪽 위 선'이 생기지 않는다
        // 셰이더 좌표는 y가 아래로 커지고 facing = dot(법선, -lightDir)이라, (0, 1)이어야 위 가장자리가 광원 쪽(lobeF)이 된다
        shader.setFloatUniform("lightDir", 0f, 1f)
        shader.setFloatUniform("specular", if (primary) 1f else 0.85f)
        shader.setFloatUniform("reflAmount", reflect)
        shader.setFloatUniform("density", density)
    }

    private fun bmpShader(b: ImageBitmap) = BitmapShader(b.asAndroidBitmap(), Shader.TileMode.DECAL, Shader.TileMode.DECAL).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

    private companion object {
        const val AGSL = """
uniform shader field;
uniform shader near;
uniform float2 size;
uniform float cornerRadius;
uniform float bevel;
uniform float refraction;
uniform float dispersion;
uniform float2 lightDir;
uniform float specular;
uniform float reflAmount; // 반사 세기. GLSL 내장 함수 이름 reflect와 겹치면 컴파일이 실패한다(1.42.15~16은 이 때문에 셰이더가 한 번도 돌지 않았다)
uniform float density;
uniform shader frost;
uniform float sizeK; // 버튼 크기 0(작은 캡슐)~1(큰 버튼): 안쪽 그늘 폭은 넓히고 세기는 낮춰 큰 버튼이 눌려 보이지 않게(11-03)
uniform float useFrost; // 1이면 미리 흐린 반사(frost) 한 장만 읽는다(11-02 최적화: 픽셀당 텍스처 114회 → 3회)

// 모서리는 원호가 아니라 초타원(|x|^N + |y|^N = r^N, SuperellipseShape.EXPONENT와 같은 N) — 잘라낸 모양과 같은 윤곽
const float N = 4.0;

float lenN(float2 v) {
    return pow(pow(v.x, N) + pow(v.y, N), 1.0 / N);
}

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return lenN(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float2 edgeNormal(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    float2 s = float2(p.x >= 0.0 ? 1.0 : -1.0, p.y >= 0.0 ? 1.0 : -1.0);
    if (max(q.x, q.y) > 0.0) {
        float2 v = max(q, 0.0);
        return s * normalize(float2(pow(v.x, N - 1.0), pow(v.y, N - 1.0)) + float2(1e-5, 1e-5));
    }
    float w = clamp(0.5 + 0.5 * (q.x - q.y) / (8.0 * density), 0.0, 1.0);
    return normalize(float2(s.x * w, s.y * (1.0 - w)) + float2(0.0, 1e-4));
}

// 반사는 CPU에서 색 보정 없이 온다(실기 속도). 근거리 띠는 5탭 블러를 하고, 합친 뒤 색 보정(채도 1.3·대비 1.12)을 한 번에 한다
half4 back0(float2 c) {
    half4 n = near.eval(c);
    half4 f = field.eval(c);
    return n + f * (1.0 - n.a);
}

// 버튼 유리 블러: 엔진이 프레임마다 미리 흐려 둔 반사(frost)를 한 번만 읽는다(예전에는 여기서 19탭 × 분산 3채널 = 텍스처 114회).
// 버튼 블러가 0이면 원래 반사(근거리 띠 + 기본)를 읽는다
half4 back(float2 c) {
    half4 m = useFrost > 0.5 ? frost.eval(c) : back0(c);
    if (m.a < 0.001) return m;
    float3 col = float3(m.rgb) / float(m.a);
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    col = mix(float3(lum), col, 1.22);
    col = clamp((col - 0.48) * 1.18 + 0.48, 0.0, 1.0);
    return half4(half3(col) * m.a, m.a);
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - hs;
    float minHalf = min(hs.x, hs.y);
    float r = min(cornerRadius, minHalf);
    float sd = sdRoundRect(p, hs, r);
    float cov = clamp(0.5 - sd, 0.0, 1.0);
    if (cov <= 0.0) return half4(0.0);

    // 1) 굴절: 가장자리 띠 안에서만, 제곱 프로파일(되접힘 고리 없이 가장자리로 갈수록 강하게)
    float2 n = edgeNormal(p, hs, min(r * 1.5, minHalf));
    float x = 1.0 - clamp(-sd / bevel, 0.0, 1.0);
    float bend = x * x;
    float2 disp = -n * (bend * refraction);

    // 2) 샘플링 + 채널 분산(파랑이 조금 더 꺾임)
    half4 g = back(coord + disp);
    half rr = back(coord + disp * (1.0 - dispersion)).r;
    half bb = back(coord + disp * (1.0 + dispersion)).b;
    half4 refl = half4(rr, g.g, bb, g.a);
    // vibrancy(QWEA0 곡선): 이미 진한 색·아주 밝은 색은 덜 올리고 탁한 색을 살린다. premultiplied라 알파로 나눠 계산
    if (refl.a > 0.001) {
        float3 col = float3(refl.rgb) / float(refl.a);
        float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
        float satNow = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
        float room = (1.0 - smoothstep(0.2, 0.85, satNow)) * (1.0 - smoothstep(0.75, 0.98, lum));
        col = clamp(mix(float3(lum), col, 1.0 + 0.35 * mix(0.3, 1.0, room)), 0.0, 1.0);
        refl = half4(half3(col) * refl.a, refl.a);
    }
    refl = refl * half(reflAmount);

    // 3) 테두리 빛: 법선·광원 내적, 광원 쪽과 반대쪽(안쪽 벽 반사) 두 lobe. 1px 실선 + 안쪽 8px 부드러운 빛
    float facing = dot(n, -lightDir);
    float lobeF = pow(max(facing, 0.0), 3.0);
    float lobeB = pow(max(-facing, 0.0), 3.0) * 0.55;
    float hair = clamp(1.0 - abs(sd + density) / (1.5 * density), 0.0, 1.0);
    float inner = -sd - density;
    float glow = clamp(inner / (2.0 * density), 0.0, 1.0) * pow(clamp(1.0 - (inner - 2.0 * density) / (8.0 * density), 0.0, 1.0), 1.5);
    // 11-02 소장님 "왼쪽 위 회색 선 거슬린다": 테두리 1px 실선(hair)은 뺐다. 안쪽 부드러운 빛만 아주 옅게 남긴다
    // 어두운 장면에서는 광원 쪽 안쪽 빛도 왼쪽 위 회색 호로 보여 함께 뺐다. 테두리는 굴절·Fresnel·안쪽 그늘로만 드러난다
    // 11-03 1.44.8에서 위 가장자리 빛·아래 그늘을 되살렸더니 버튼이 볼록하게 튀어나와 보였다(소장님 "볼록 튀어나온 거 에반데") — 다시 끈다
    float spec = 0.0 * specular * glow * lobeF;

    // 4) Fresnel: 가장자리로 갈수록 반사가 밝아진다(시야각 효과 근사)
    float fres = pow(x, 4.0) * 0.05;

    // 5) 안쪽 가장자리 그늘(두께감): 테두리 바로 안쪽이 살짝 어둡다
    float sw = (4.0 + 6.0 * sizeK) * density;
    float shade = clamp(1.0 - abs(-sd - sw) / sw, 0.0, 1.0) * 0.18 * (1.0 - 0.4 * sizeK) * (1.0 - lobeF);

    half hl = half(clamp(spec + fres, 0.0, 1.0));
    half4 outc = refl + half4(hl) * (1.0 - refl.a);
    outc = outc + half4(0.0, 0.0, 0.0, half(shade)) * (1.0 - outc.a);
    return outc * half(cov);
}
"""
    }
}
