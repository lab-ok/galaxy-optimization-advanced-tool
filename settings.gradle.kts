pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "GOAT"
include(":app")
// 공통 모듈: 기능들이 함께 쓰는 코드와 모든 리소스
include(":core")
// 기능별 모듈(11-11 소장님 "각 기능별로 코드나 모듈은 나누면 안돼? 공통코드는 그대로"). 상단바 숨기기로 시범
include(":feature:statusbar")
