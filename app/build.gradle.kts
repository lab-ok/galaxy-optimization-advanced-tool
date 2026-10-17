plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.labok.goat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.labok.goat"
        // Android 12 이상(방울 효과에 RenderEffect 필요). Android 11은 지원하지 않는다(사용자 결정).
        minSdk = 31
        targetSdk = 35
        versionCode = 101
        versionName = "1.14.2"
        // 실험실 기능은 lab 빌드에만 켠다. 공개 release·debug에는 들어가지 않는다.
        buildConfigField("boolean", "LAB", "false")
        // 대상 기기(갤럭시 폴드)는 모두 arm64다. 라이브러리가 넣는 다른 ABI의 .so는 싣지 않는다.
        ndk { abiFilters += "arm64-v8a" }
        // 앱 문구는 한국어뿐이다(Copy.kt). 라이브러리 번역 중 한국어만 남긴다(기본 영어는 그대로 남는다).
        resourceConfigurations += "ko"
    }

    buildTypes {
        release {
            // 축소·최적화(R8)를 켠다. 숨은 API는 프레임워크 클래스라 지워지지 않고,
            // 리플렉션으로 부르는 앱·라이브러리 코드와 이름 유지(난독화 끔)는 proguard-rules.pro에 둔다.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
        // 개발자 본인용 실험실 빌드: release와 같고(R8 설정 포함) 실험실 메뉴만 켠다(같은 서명이라 덮어 설치된다).
        create("lab") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "LAB", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // 화면 캡처 테스트(screenshot 패키지)는 Robolectric 네이티브 그래픽이 필요하다.
                // ARM64 호스트에는 그 라이브러리가 없어 -Pscreenshot=<x86 java 래퍼> 로만 x86_64 JVM(qemu)에서 돌린다.
                val x86 = project.findProperty("screenshot") as String?
                if (x86 != null) {
                    test.executable = x86
                    test.filter.includeTestsMatching("com.labok.goat.screenshot.*")
                    test.systemProperty("roborazzi.test.record", "true")
                    // qemu 안의 JVM은 DNS가 불안정하다. Robolectric 런타임 jar는 미리 받아 둔 것을 쓴다.
                    test.systemProperty("robolectric.offline", "true")
                    test.systemProperty(
                        "robolectric.dependency.dir",
                        System.getProperty("user.home") + "/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-12650502-i7"
                    )
                    test.maxHeapSize = "3g"
                } else {
                    test.exclude("**/screenshot/**")
                }
            }
        }
    }
    lint {
        // 개인용 sideload 앱이다. targetSdk 갱신 경고 때문에 빌드를 멈추지 않는다.
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // UI: Jetpack Compose + Material 3 (dynamic color, edge-to-edge)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // 부팅 뒤 지연 재확인
    implementation(libs.androidx.work.runtime.ktx)
    // 반쯤 접힘(플렉스) 자세 감지: 실험실 플렉스 모드 시험 화면이 위아래로 나뉜다
    implementation(libs.androidx.window)
    // IWindowManager 숨은 API 접근
    implementation(libs.hiddenapibypass)
    // Shizuku: PC 없이 셸 권한 호출(기본 권한 받기, 앱마다 크기 따로)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    // 위쪽 제목 줄 뒤 반투명 블러(Compose 1.7에 맞는 판)
    implementation(libs.haze)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
