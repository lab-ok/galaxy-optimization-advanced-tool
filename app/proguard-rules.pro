# GOAT R8 규칙 (release·lab 빌드)
#
# 축소·최적화만 켜고 이름은 바꾸지 않는다.
# - 로그·진단 파일에 남는 예외/클래스 이름(javaClass.simpleName, 스택 추적)을 그대로 읽으려고
# - WorkManager가 DB에 저장해 둔 작업 클래스 이름, 설정에 저장된 enum 이름이 판이 바뀌어도 그대로여야 해서
-dontobfuscate

# 셸 도우미: PC에서 `app_process ... com.labok.goat.data.appsize.DensityServer`로 띄운다.
# 앱 코드에서는 main을 부르지 않으므로 지우지 않게 남긴다.
-keep class com.labok.goat.data.appsize.DensityServer {
    public static void main(java.lang.String[]);
}

# Shizuku 셸 실행: 공개 함수가 없어 리플렉션으로 Shizuku.newProcess(String[], String[], String)를 부른다(ShizukuAccess.newProcess).
# 앱 코드에서 직접 부르지 않으므로 R8이 지우지 않게 남긴다. 돌려받는 ShizukuRemoteProcess도 함께 둔다.
-keep class rikka.shizuku.Shizuku {
    private static rikka.shizuku.ShizukuRemoteProcess newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }

# 그 밖의 리플렉션 대상(IWindowManager·IActivityTaskManager·ServiceManager 등)은 모두 프레임워크 클래스라 축소 대상이 아니다.
# hiddenapibypass·WorkManager·androidx.window·Shizuku provider는 AAR에 든 소비자 규칙을 쓴다.
