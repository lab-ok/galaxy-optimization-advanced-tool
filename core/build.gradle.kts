// 공통 모듈: 여러 기능이 함께 쓰는 코드(리퀴드 UI·공통 컴포넌트·문구·그림 도구·기록·Shizuku·기기 판별·새 기능 점)와 모든 리소스.
// 기능 모듈과 앱이 이 모듈에 기대고, 이 모듈은 어떤 기능도 모른다(기능별 연결은 앱이 시작할 때 등록한다).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.labok.goat.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    api(libs.androidx.core.ktx)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.kotlinx.coroutines.core)
    api(libs.shizuku.api)
    implementation(libs.hiddenapibypass)
    testImplementation(libs.junit)
}
