package com.local.folddpifix.ui.home

import com.local.folddpifix.background.FoldWatchService
import com.local.folddpifix.background.DensityWatchWorker
import com.local.folddpifix.background.ExternalChangeNotifier
import com.local.folddpifix.data.display.DpiManager
import com.local.folddpifix.data.log.LogRepository
import com.local.folddpifix.data.settings.SettingsRepository
import com.local.folddpifix.domain.DpiFixer
import com.local.folddpifix.domain.DpiPolicy
import com.local.folddpifix.domain.ScreenPolicy
import com.local.folddpifix.ui.text.Copy
import android.app.Application
import android.net.Uri
import com.local.folddpifix.data.log.CrashRecorder
import com.local.folddpifix.data.log.DiagnosticReport
import com.local.folddpifix.data.log.PublicLogFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.local.folddpifix.domain.PanelSpec
import com.local.folddpifix.domain.ScreenLearner
import com.local.folddpifix.domain.plan
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 홈 화면 상태와 사용자 동작. binder·파일 접근은 모두 IO 스레드에서 한다. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val DISPLAY_DEBOUNCE_MS = 700L
    }

    private val settings = SettingsRepository.from(app)
    private val logRepo = LogRepository.from(app)
    private val dpi = DpiManager(app)
    private val fixer = DpiFixer(dpi, settings, logRepo::add)
    private val learner = ScreenLearner(settings, logRepo::add)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 한 번만 보여 줄 짧은 알림(토스트). */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    private var lastPermission: Boolean? = null
    private var displayJob: Job? = null
    private var lastActiveKey: String? = null

    init {
        // 기준은 항상 외부 화면(사용자 결정). 내부 화면 크기는 기기 정보로 계산한다.
        if (settings.reference != ScreenPolicy.Role.OUTER) {
            settings.reference = ScreenPolicy.Role.OUTER
            logRepo.add("기준 화면을 외부로 고정")
        }
        // 설치·업데이트 직후에도 DPI 변경 감지가 걸려 있도록 한다(이미 걸려 있으면 그대로).
        DensityWatchWorker.sync(app)
    }

    fun refresh() = viewModelScope.launch {
        val next = withContext(Dispatchers.IO) { snapshot() }
        if (lastPermission == false && next.hasPermission) say(Copy.TOAST_PERMISSION_OK)
        lastPermission = next.hasPermission
        _state.value = next.copy(busy = _state.value.busy)
    }

    /**
     * 앱이 열린 상태에서 접기/펼치기 등으로 디스플레이가 바뀌면 잠시 뒤 한 번만 확인·갱신한다.
     * 디스플레이 이벤트는 밝기·주사율 변화로도 자주 오므로, 켜진 화면이 실제로 바뀐 경우에만 적용 확인을 한다.
     */
    fun onDisplayChanged() {
        displayJob?.cancel()
        displayJob = viewModelScope.launch {
            delay(DISPLAY_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                val key = dpi.currentScreen()?.key
                if (key != null && key != lastActiveKey) {
                    lastActiveKey = key
                    if (settings.autoRecover) fixer.ensure("디스플레이 전환(앱)", automatic = true)
                    // 처음 보는 화면은 두 번 읽혀야 학습된다. 잠시 뒤 한 번 더 확인해 학습과 보정 적용을 마친다.
                    if (ScreenLearner.hasPendingCandidate) {
                        delay(ScreenLearner.STABLE_MS + 200)
                        if (settings.autoRecover) fixer.ensure("디스플레이 전환 확인(앱)", automatic = true)
                        else learner.observe(dpi.currentScreen(), "화면")
                    }
                }
            }
            refresh()
        }
    }

    fun messageShown() {
        _messages.value = null
    }

    fun say(text: String) {
        _messages.value = text
    }

    /** 외부 화면 크기를 정하고 바로 적용한다(슬라이더를 놓을 때). */
    fun commitOuter(value: Int) {
        if (!DpiPolicy.isValid(value)) return
        if (settings.targetDpi != value) {
            logRepo.add("외부 화면 크기: ${settings.targetDpi} → $value")
            settings.targetDpi = value
        }
        apply(quiet = false)
    }

    /** 내부 화면 미세조정을 저장하고 바로 적용한다. */
    fun commitAdjust(value: Int) {
        val v = value.coerceIn(-SettingsRepository.MAX_ADJUST, SettingsRepository.MAX_ADJUST)
        if (settings.adjust != v) {
            logRepo.add("내부 화면 미세조정: ${settings.adjust} → $v")
            settings.adjust = v
        }
        apply(quiet = false)
    }

    fun applyNow() = apply(quiet = false)

    private fun apply(quiet: Boolean) = runBusy {
        when (fixer.ensure("수동", automatic = false)) {
            DpiPolicy.Decision.APPLY -> if (settings.lastApply?.success == true) Copy.TOAST_APPLIED else Copy.TOAST_FAILED
            DpiPolicy.Decision.ALREADY_OK -> if (quiet) null else Copy.TOAST_APPLIED
            DpiPolicy.Decision.NO_PERMISSION -> Copy.TOAST_NO_PERMISSION
            DpiPolicy.Decision.RATE_LIMITED -> Copy.TOAST_BUSY
            DpiPolicy.Decision.BLOCKED -> Copy.STATUS_HOLD
            else -> null
        }
    }

    /** 자동 적용 스위치 하나로 자동 복구·부팅 적용·전환 감지를 함께 켜고 끈다. */
    fun setAuto(on: Boolean) {
        settings.autoRecover = on
        settings.applyOnBoot = on
        settings.watchFold = on
        logRepo.add("자동 적용 ${if (on) "켜짐" else "꺼짐"}")
        FoldWatchService.syncWithSettings(getApplication())
        DensityWatchWorker.sync(getApplication())
        refresh()
    }

    fun resetToDefault() = runBusy {
        settings.autoRecover = false
        settings.applyOnBoot = false
        settings.watchFold = false
        val r = dpi.reset()
        logRepo.add(if (r.success) "기본 크기 복원 성공 (${r.method}) ${r.detail}, 자동 적용 끔" else "기본 크기 복원 실패: ${r.detail}")
        settings.externalChange = null
        FoldWatchService.syncWithSettings(getApplication())
        DensityWatchWorker.sync(getApplication())
        if (r.success) Copy.TOAST_RESET else Copy.TOAST_FAILED
    }

    /**
     * 권한 초기화의 앱 쪽 단계: 자동 적용·감지를 모두 끄고, 앱이 기억한 적용값과 외부 변경 기록을 지운다.
     * 권한 자체는 Android가 앱의 자체 회수를 막아 두어 PC의 pm revoke로 제거한다. 제거되면 앱이 알아채 설정 안내를 띄운다.
     */
    fun resetPermissionState() = runBusy {
        settings.autoRecover = false
        settings.applyOnBoot = false
        settings.watchFold = false
        settings.appliedDpi = emptyMap()
        settings.externalChange = null
        FoldWatchService.syncWithSettings(getApplication())
        DensityWatchWorker.sync(getApplication())
        ExternalChangeNotifier.cancel(getApplication())
        logRepo.add("권한 초기화: 자동 적용·감지 끔, 적용 기록 삭제. PC에서 권한 회수 대기")
        Copy.TOAST_PERMISSION_RESET
    }

    /** 외부 DPI 변경에 대한 선택: [adopt]면 바뀐 값을 기준으로, 아니면 앱 설정으로 복원. */
    fun resolveExternal(adopt: Boolean) = runBusy {
        ExternalChangeNotifier.cancel(getApplication())
        when (fixer.resolveExternal(adopt, if (adopt) "앱: 변경값 기준" else "앱: 복원")) {
            DpiPolicy.Decision.APPLY, DpiPolicy.Decision.ALREADY_OK ->
                if (settings.lastApply?.success == false) Copy.TOAST_FAILED else if (adopt) Copy.TOAST_ADOPTED else Copy.TOAST_RESTORED
            DpiPolicy.Decision.BLOCKED -> Copy.STATUS_HOLD
            DpiPolicy.Decision.NO_PERMISSION -> Copy.TOAST_NO_PERMISSION
            else -> null
        }
    }

    /** 크기 테스트의 자를 카드로 맞춘 PPI를 저장한다. null이면 지운다(기기 값으로). */
    fun setRulerPpi(key: String, ppi: Float?) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            settings.rulerPpi = if (ppi == null) settings.rulerPpi - key else settings.rulerPpi + (key to ppi)
            logRepo.add("크기 테스트 자 보정: $key → ${ppi?.let { "%.1f ppi".format(it) } ?: "기기 값"}")
        }
        refresh()
    }

    /** 학습한 화면 정보를 지우고 지금 켜진 화면부터 다시 배운다. */
    fun resetLearned() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            logRepo.add("학습 정보 초기화 (이전: ${settings.screens.joinToString(",") { it.key }}, 비활성 보고값 ${settings.inactivePpi})")
            settings.clearLearnedScreens()
        }
        say(Copy.TOAST_RELEARN)
        refresh()
    }

    private fun runBusy(block: () -> String?) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        val msg = withContext(Dispatchers.IO) { block() }
        _state.update { it.copy(busy = false) }
        msg?.let(::say)
        refresh()
    }

    /**
     * 진단 로그를 Download/FoldFit에 파일로 저장한다. 공유용으로 진단 파일과 오늘 로그 파일의 Uri를 돌려준다.
     * 저장에 실패하면 빈 목록.
     */
    suspend fun exportDiagnostics(): List<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val name = "foldfit-diagnostic-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}.txt"
            val saved = PublicLogFile.saveNew(app, name, DiagnosticReport.build(app)) ?: error("파일 생성 실패")
            logRepo.add("진단 로그 저장: ${PublicLogFile.displayPath}$name")
            listOfNotNull(saved, PublicLogFile.todayUri(app))
        }.getOrElse {
            logRepo.add("진단 로그 저장 실패: ${it.javaClass.simpleName}: ${it.message}")
            emptyList()
        }
    }

    fun clearDiagnostics() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            logRepo.clear()
            CrashRecorder.clear(getApplication())
        }
        refresh()
    }

    private fun snapshot(): UiState {
        val screen = dpi.currentScreen()
        learner.observe(screen, "화면")
        val displays = dpi.displays()
        displays.forEach { d -> if (d.id != 0 && d.screen?.key != screen?.key) learner.observeInactive(d.id, d.screen) }
        val plan = settings.plan()
        val external = settings.externalChange ?: fixer.checkExternal("앱 화면")
        return UiState(
            external = external,
            rulerPpi = settings.rulerPpi,
            loading = false,
            hasPermission = dpi.hasPermission(),
            dpi = dpi.read(),
            target = settings.targetDpi,
            reference = settings.reference,
            adjust = settings.adjust,
            plan = plan,
            activeScreen = screen,
            effective = plan?.targetFor(screen),
            autoRecover = settings.autoRecover,
            applyOnBoot = settings.applyOnBoot,
            watchFold = settings.watchFold,
            lastApply = settings.lastApply,
            displays = displays,
            hasCrash = CrashRecorder.read(getApplication()).isNotBlank(),
            inactivePpi = settings.inactivePpi,
        )
    }
}
