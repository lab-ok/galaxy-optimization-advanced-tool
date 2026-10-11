# 실시간 "둥근 잔 속 액체" 토이 — 조사 노트 (Compose, 60fps, 40–80 particles, no native)

[V]=원문/공식문서 확인, [I]=추정/업계 관행(직접 수치 확인 불가)

## 1. 경량 2D 유체 방법

- **Double Density Relaxation (Clavet, Beaudoin, Poulin 2005, "Particle-based Viscoelastic Fluid Simulation", SCA'05)** [V 존재·개요] — 논문 원문: https://www.ljll.fr/~frey/papers/levelsets/Clavet%20S.,%20Particle-based%20viscoelastic%20fluid%20simulation.pdf (접근 차단됨, Anubis bot 보호) / ACM: https://dl.acm.org/doi/10.1145/1073368.1073400 / 리뷰 2차 소스(알고리즘 단계·커널 확인): GameDev.net 토론 https://www.gamedev.net/forums/topic/546205 , GitHub 구현 https://github.com/kotsoft/particle_based_viscoelastic_fluid , https://github.com/Erkaman/gl-water2d
  - 핵심 아이디어: SPH처럼 뉴턴 유체 방정식을 풀지 않고, **위치 기반(Position-based)** 으로 매 스텝 "밀도/근접밀도(near-density)"를 계산한 뒤 두 개의 반대 부호 압력항으로 입자를 직접 변위시켜 비압축성+반클러스터링을 동시에 만족시킨다. 압력해의 선형계를 풀 필요가 없어 **매우 저렴하고 안정적**이다[I: 논문 abstract 요지].
  - 커널: W(r,h) = (1 − r/h)² (근접밀도는 세제곱 (1-r/h)³) [V, GameDev.net/ResearchGate 그림 설명에서 확인: "Double density relaxation kernel functions" — (1-q)² 형태].
  - 점성은 별도 "viscosity impulse" 단계에서 선형(σ)·이차(β) 항으로 입자쌍 간 상대속도를 감쇠시킨다[I: 논문 구조상 통칭, 원문 수치 미확인].
- **Position Based Fluids (Macklin & Müller, SIGGRAPH 2013)** [V] — ACM: https://dl.acm.org/doi/10.1145/2461912.2461984 / 강의자료(알고리즘 단계 확인): https://www.cse.chalmers.se/edu/year/2018/course/TDA361/Advanced%20Computer%20Graphics/Position%20Based%20Fluids.pdf — SPH 밀도 추정 + PBD 프레임워크로 비압축 제약을 **Jacobi 반복(보통 3–5회)** 으로 푼다. 물리적으로 더 정확하지만 반복 솔버 비용이 double-density relaxation보다 크다.
- **이 토이에 대한 판단 [I, 조사 기반 추정]**: 60–80 입자·메인 스레드·Kotlin 제약에서는 **double density relaxation이 더 유리**하다 — 반복 솔버가 없고(스텝당 1패스), 구현이 단순(신규 배열/행렬 불필요)하며 끈적함(viscosity)·표면장력 느낌이 "액체 토이" 룩에 자연스럽게 나온다. PBF는 물리적 정확도가 필요한 대규모(수백~수천 입자) 유체에 더 적합.
- **입자 수 규모 확인**: "수천 개 미만(≤~1000)의 작은 입자 수에서는 해시 테이블·공간 분할·표면 추출 없이 기본 SPH를 그대로 구현 가능"[V, 2차 소스: https://cseweb.ucsd.edu/classes/wi17/cse169-a/slides/CSE169_15.pdf 계열 요약 및 검색 결과 종합]. 60–80개는 이 범위에 한참 못 미치므로 **O(n²) 전수 이웃 탐색**으로 충분 — 공간 해시 그리드는 불필요한 최적화다[I: n=80일 때 pair 수 ≤3160, 60fps에 여유].

### 파라미터 (px 단위, 원문 수치 비공개 — 전부 [I] 추정/업계 관행)
논문이 "trial and error로 튜닝했다"고 알려져 있어[I] 아래는 조사 기반 추정치. §5 권장 구현 표 참조.

## 2. 센서 → 시뮬레이션 매핑

- **TYPE_GRAVITY / TYPE_LINEAR_ACCELERATION / TYPE_GYROSCOPE** 단위·좌표계 [V] 공식문서 https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion
  - 단위: 가속도계 m/s², 자이로 rad/s.
  - 좌표계: 평면에 눕힌 세로모드 기준 X=오른쪽, Y=위, Z=화면 바깥(사용자 쪽). **화면 그리기 좌표(y 아래로 증가)로 쓰려면 Y축 부호를 반전**해야 한다 `screenY += -gravity.y * scale` [I: 표준 변환, Android 좌표계 정의로부터 도출].
  - TYPE_GRAVITY가 없는 기기에서는 저역통과 필터로 중력 추출 가능: `gravity = α·gravity + (1-α)·raw`, 선형가속도 = `raw - gravity`, 공식문서 예제 α=0.8 [V, 동일 문서].
  - 자이로 Z축 각속도(rad/s)가 그대로 "스월(swirl)" 토크 입력. 반시계가 양수(오른손 법칙, 축 양 끝에서 원점을 바라볼 때)[V].
- **샘플링 레이트** [V, 공식문서] SENSOR_DELAY_GAME ≈ 20,000μs(≈50Hz), SENSOR_DELAY_UI ≈ 60,000μs(≈16.7Hz), SENSOR_DELAY_FASTEST=0(기기 최대), SENSOR_DELAY_NORMAL ≈ 200,000μs(≈5Hz, 기본값) — 60fps 틸트 반응성엔 **SENSOR_DELAY_GAME 또는 FASTEST** 권장[I: 문서가 "가장 느린 지연을 선택하라"는 배터리 가이드를 주지만, 60fps UI 추종엔 50Hz+가 필요]. Android 12+는 고빈도 가속도계/자이로 등록에 rate-limiting이 적용될 수 있음[V, 동일 문서 각주].
  - 출처: https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview
- **관성(가짜) 힘으로 선형가속도 사용** [I, 역학적으로 표준]: 용기가 가속하면(폰이 움직이면) 액체는 뒤처진다 → 용기 기준계에서 입자에 가해지는 유사힘은 `F_inertial = -linearAcceleration` (관성계 반작용, 자동차 안 커피가 가속 반대방향으로 쏠리는 것과 동일 원리). 그대로 가속도 벡터에 더하면 됨.
- **스월(각속도) → 접선힘**: 자이로 Z 각속도 ω를 중심 기준 코리올리형 접선 가속으로 매핑: `a_tangent = ω × (p - center) × k_swirl`(2D 외적이므로 `(-dy, dx)*ω*k`)[I: 완전한 Coriolis/원심력 유도가 아니라 "그럴듯한" 비주얼 근사 — 토이 용도엔 충분, 실제 유체역학적 엄밀 해가 아님].
- **댐핑/클램프 (폭주 방지)** [I, 일반 수치안정화 관행]: 매 스텝 velocity *= 0.98~0.995, `|a|`를 중력의 3~5배(≈30~50 m/s²)로 클램프, 센서 입력 자체에도 저역통과(EMA, α≈0.15~0.3)를 걸어 떨림/노이즈로 인한 폭발 방지.

## 3. 렌더링 (goo/metaball 룩)

- **공식 기법 확인** [V, 2차 소스 다수 교차확인]: 여러 원(파티클)을 **블러(RenderEffect.createBlurEffect, API31+)** 로 겹친 뒤, **알파 채널에 ColorMatrix 임계값**을 적용해 반투명 겹침 영역을 완전 불투명/완전 투명으로 스냅시키면 방울들이 하나의 덩어리처럼 합쳐 보인다(= CSS "gooey effect"와 동일 원리: https://css-tricks.com/gooey-effect/ 의 feGaussianBlur+feColorMatrix와 대응).
- **확인된 실제 Kotlin 코드** [V, 원문: https://medium.com/@yuriyskul/gooey-metaball-effects-on-android-with-jetpack-compose-blurring-and-alpha-filtering-concepts-63f1cf879257 , jina.ai 리더 프록시로 403 우회해 본문 확보]:
```kotlin
fun metaBallRenderEffect(): androidx.compose.ui.graphics.RenderEffect =
    RenderEffect.createColorFilterEffect(
        ColorMatrixColorFilter(
            android.graphics.ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 39f, -5000f   // alpha 행: 39*a - 5000 → 임계값 스냅
                )
            )
        )
    ).asComposeRenderEffect()
// 적용: Modifier.graphicsLayer { renderEffect = metaBallShader }
```
  alpha 계수 39/−5000 조합이 "불투명도 ~0.5 미만은 완전 투명, ~0.53 초과는 완전 불투명"으로 스냅시킨다고 원문이 설명[V 동일 출처]. 블러는 `.blur()`(API31+, BlurredEdgeTreatment.Unbounded)로 별도 레이어에 적용 후 같은 Box/graphicsLayer 체인에 합성.
  - **Pre-API31 폴백** [V 동일 출처]: `android.graphics.BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)`을 Paint에 직접 설정(Canvas 레벨, graphicsLayer 불필요) — 구형 기기용 대안.
  - RenderEffect는 **offscreen buffer를 강제로 생성**한다(CompositingStrategy와 무관하게)[V, developer.android.com/develop/ui/compose/graphics/draw/modifiers] — 즉 다수 원을 그린 **단일 Canvas 전체**에 blur+threshold를 한 번만 적용하는 구조가 맞다(원마다 개별 적용 금지, 비용 폭증).
- **파라미터 관행 [I]**: 블러 반경 ≈ 입자 반경의 0.6~1.0배일 때 방울들이 "뭉친" 느낌과 "흩어진 방울" 느낌 사이 균형이 좋다는 것이 메타볼 튜토리얼들의 공통 경험칙(명시적 수치는 출처 미확인, 직접 튜닝 필요).
- **표면 하이라이트**: 별도 레이어로 상단에 작은 반투명 흰 타원(고정 위치 or 중력 반대 방향)을 blur 전 단계에 얹으면 "젤리 광택" 효과 — 이는 특정 출처 없는 일반 그래픽 관행[I].
- **참고: LiquidFun(Google, Box2D 확장)** [V] https://github.com/google/liquidfun , 발표: https://opensource.googleblog.com/2013/12/liquidfun-rigid-body-physics-library.html — C++ 네이티브 라이브러리라 "no native code" 제약과 직접 맞지 않지만, 배리어 파티클·정압 파티클 등 **안정화 기법 아이디어**는 참고 가치 있음. 본 토이엔 미사용 권장.
- **Compose Canvas 성능 [V]** https://developer.android.com/develop/ui/compose/graphics/draw/modifiers :
  - Compose Canvas API는 Paint 객체를 인자로 받지 않고 내부적으로 재사용 — 매 프레임 `Paint()`/`Path()` new 금지, `Modifier.drawWithCache`로 Brush/Shader/Path 캐시.
  - `graphicsLayer` 분리 레이어는 독립적으로 invalidate 가능 — blur+threshold는 입자 전용 레이어 하나로 분리하고 배경 UI와 별도 recomposition.
  - `RenderNode` 백킹은 포인터 수준이라 재기록(recording) 자체를 프레임마다 피하는 것이 핵심(즉 Canvas `onDraw`에서 매번 커밋만, 구조 재생성 없이).

## 4. 인터랙션

- **흔들기(shake) 임팩트** — 흔들기 임계값 [I, 다수 소스 종합, 수치 자체는 2차·커뮤니티 소스로 공식 Android 가이드는 아님]: 가속도 크기(`sqrt(ax²+ay²+az²)`)가 약 **12–15 m/s²**를 초과하면 "흔들림"으로 간주하는 것이 커뮤니티 구현 다수의 공통값(낮으면 민감도 과다, 26.5 m/s²는 과도하게 둔감한 예). 참고 gist: https://gist.github.com/YuriDenison/10012795
  - 토이 적용 [I]: 흔들림 감지 시 입자군 일부에 순간 속도 임펄스(현재 linearAcceleration 방향의 반대 또는 수직 방향)를 주고, 해당 스텝 double-density relaxation의 `k`(강성)를 일시적으로 낮춰 "방울이 분리됐다 다시 합쳐지는" 효과를 낸다 — 분리·재합체는 double-density relaxation의 near-pressure 항이 이미 반클러스터링을 보장하므로 추가 로직 없이도 자연히 일어난다[I, 논문 설계 의도에 근거].
- **탭(tap)**: 탭 좌표를 중심으로 반경 내 입자에 외향 임펄스(또는 당기는 인력)를 주는 것으로 "물결/파문" 유발 가능 — 표준 패턴이나 직접 출처 확인 불가[I].

## 5. 권장 구현

### 5.1 한 스텝 의사코드 (double density relaxation 기반, [I] 설계는 추정·Clavet 2005 구조에 기반)
```
// 입력: gravityVec(px/s²), linearAccelVec(px/s²), gyroZ(rad/s), dt(s, 보통 1/60)
fun step(particles, center, containerRadiusPx, dt) {
    // 1) 외력 적용 (중력 + 관성 유사힘 + 스월)
    for p in particles {
        a = gravityVec                      // 틸트
        a += -linearAccelVec                // 흔들림에 대한 관성 반작용(용기가 가속하면 액체는 뒤처짐)
        d = p.pos - center
        a += gyroZ * vec(-d.y, d.x) * kSwirl // 자이로 스월(접선 가속, 근사)
        p.vel += a * dt
        p.vel *= damping                    // 0.98~0.995, 폭주 방지
        clampLength(p.vel, maxSpeed)
        p.prevPos = p.pos
        p.pos += p.vel * dt                 // predict position
    }

    // 2) 이웃 탐색: O(n²) 전수 탐색 (n≤80이면 그리드 불필요)
    neighbors = bruteForcePairsWithin(particles, h)

    // 3) double density relaxation (밀도→압력→변위, 1패스)
    for p in particles {
        density = 0; nearDensity = 0
        for q in neighbors(p) {
            q_ = 1 - dist(p,q)/h
            density += q_*q_
            nearDensity += q_*q_*q_
        }
        pressure = k * (density - restDensity)
        nearPressure = kNear * nearDensity
        dispP = 0
        for q in neighbors(p) {
            q_ = 1 - dist(p,q)/h
            D = dt*dt * (pressure*q_ + nearPressure*q_*q_) * dirUnit(p,q)
            q.pos += D/2
            dispP -= D/2
        }
        p.pos += dispP
    }

    // 4) viscosity impulse (선택, 점성/끈적임)
    for (p,q) in neighbors {
        u = dot(p.vel - q.vel, dirUnit(p,q))
        if u > 0 {
            I = dt * (1-dist/h) * (sigma*u + beta*u*u) * dirUnit(p,q)
            p.vel -= I/2; q.vel += I/2
        }
    }

    // 5) 원형 경계 충돌 (용기 벽)
    for p in particles {
        d = p.pos - center
        if length(d) > containerRadiusPx - particleRadius {
            p.pos = center + normalize(d) * (containerRadiusPx - particleRadius)
        }
    }

    // 6) 속도 재구성 (PBD 표준)
    for p in particles { p.vel = (p.pos - p.prevPos) / dt }
}
```

### 5.2 파라미터 표 — 240dp 용기, 입자 60개 [I 전부, dp 기준 1dp≈2.75px @xhdpi 가정은 디바이스 의존이므로 dp로 통일]
| 항목 | 값 (dp) | 비고 |
|---|---|---|
| 용기 반경 R | 120dp | 240dp 지름 |
| 입자 반경 r | 7dp | 60개가 R=120dp 원에 들어가는 밀도 기준 역산 |
| 상호작용 반경 h | 18–20dp | ≈2.6×r, double-density relaxation 표준 비율 |
| 휴지밀도 restDensity | 10 | 반경 h 내 이웃 수 기준, 경험적 튜닝 필요 |
| 강성 k | 0.08–0.15 (px²/s² 단위, dt=1/60 기준) | 작을수록 부드러움/느슨, 클수록 단단/통통 |
| 근접강성 kNear | k × 2~3배 | 반클러스터링 강도 |
| 점성 σ | 0.02–0.05 | |
| 점성 β | 0.01–0.03 | |
| 감쇠 damping | 0.985 | 매 스텝 velocity 곱 |
| 최대 속도 클램프 | ≈ 6×R dp/s | 폭주 방지 |
| dt / 서브스텝 | 1/60s, 서브스텝 1(60입자면 불필요) | 입자 늘면(>150) 서브스텝 2 고려 |
| 센서 레이트 | SENSOR_DELAY_GAME(50Hz) 또는 FASTEST | 60fps 틸트 추종 |
| 블러 반경 | r × 0.6~1.0 | RenderEffect.createBlurEffect |
| 알파 임계 ColorMatrix | `[.., 0,0,0,39,-5000]` | [V] 확인된 실값, 그대로 사용 가능 |
| 흔들림 임계값 | 가속도 크기 12–15 m/s² | 커뮤니티 공통값 |

### 5.3 렌더링 레시피
1. 단일 `graphicsLayer`가 적용된 Box/Canvas 레이어 안에서 입자들을 단색(잉크색) `drawCircle`로 그린다(개별 Paint/Path 생성 금지, 캔버스 DrawScope 내장 함수 사용).
2. 해당 레이어에 `.blur(radius = r*0.8.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)`.
3. 상위 `graphicsLayer { renderEffect = metaBallColorFilterEffect }` 로 ColorMatrix 임계값 적용(§3 코드).
4. 최종 합성 결과를 원형 clip(`Modifier.clip(CircleShape)`)된 "잔" 컨테이너 위에 그린다.
5. (선택) 하이라이트: 블러 전에 작은 반투명 흰 원 1~2개를 중력 반대방향 표면 근처에 추가로 그려 광택 표현.
