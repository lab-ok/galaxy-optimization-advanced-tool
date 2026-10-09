plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.local.folddpifix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.local.folddpifix"
        // Android 12 이상(방울 효과에 RenderEffect 필요). Android 11은 지원하지 않는다(사용자 결정).
        minSdk = 31
        targetSdk = 35
        versionCode = 26
        versionName = "1.0"
    }

    buildTypes {
        release {
            // 숨은 API를 reflection으로 부르므로 축소·난독화를 켜지 않는다.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
                    test.filter.includeTestsMatching("com.local.folddpifix.screenshot.*")
                    test.systemProperty("roborazzi.test.record", "true")
                    // qemu 안의 JVM은 DNS가 불안정하다. Robolectric 런타임 jar는 미리 받아 둔 것을 쓴다.
                    test.systemProperty("robolectric.offline", "true")
                    test.systemProperty(
                        "robolectric.dependency.dir",
                        System.getProperty("user.home") + "/.m2/repository/org/robolectric/android-all-instrumented/15-robolectric-12650502-i7",
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
    // IWindowManager 숨은 API 접근
    implementation(libs.hiddenapibypass)

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
