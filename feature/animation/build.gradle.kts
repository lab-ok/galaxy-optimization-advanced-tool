// 기능 모듈: 애니메이션 속도. 공통 코드는 :core에서 쓰고, 앱이나 다른 기능 모듈은 모른다(앱에 부탁할 일은 AppHooks로)
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.labok.goat.feature.animation"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
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
    implementation(project(":core"))
    testImplementation(libs.junit)
}
