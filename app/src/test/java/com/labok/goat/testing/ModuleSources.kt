package com.labok.goat.testing

import java.io.File

/**
 * 소스 검사 테스트(문구·간격·연결)가 모든 모듈을 보게 한다: :app, :core, :feature:*(11-11 모듈 분리).
 * 앱 모듈만 보면 공통·기능 모듈로 옮긴 파일을 검사에서 놓친다.
 */
object ModuleSources {
    /** 저장소 맨 위(테스트는 app 폴더에서 돈다). */
    val repo: File by lazy { listOf(File(".."), File(".")).map { it.absoluteFile.normalize() }.first { File(it, "core").isDirectory && File(it, "app").isDirectory } }

    /** 모듈 폴더들: app, core, feature 아래 각 모듈. */
    val modules: List<File> by lazy {
        listOf(File(repo, "app"), File(repo, "core")) + File(repo, "feature").listFiles().orEmpty().filter { File(it, "src/main").isDirectory }.sortedBy { it.name }
    }

    /** 각 모듈의 src/main/java. */
    val javaRoots: List<File> by lazy { modules.map { File(it, "src/main/java") }.filter { it.isDirectory } }
}
