// 상단바 숨기기 기능 모듈. 공통 코드는 :core에서 쓰고, 앱이나 다른 기능 모듈은 모른다
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.labok.goat.feature.statusbar"
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
}

dependencies {
    // 공통 코드(Shizuku·기록·리퀴드·문구)는 :core에서 그대로 쓴다
    implementation(project(":core"))
    testImplementation(libs.junit)
}
