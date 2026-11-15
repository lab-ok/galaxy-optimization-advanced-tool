# Diagnostic app only

This is a separate app (`com.labok.goat.keyprobe`) used to compare ordinary activity key callbacks, accessibility key callbacks, and screen-state broadcasts. It does not change rotation and is not the GOAT experimental feature.

Build with the repository's Gradle wrapper, Android SDK 35, and JDK 17:

```sh
/path/to/GOAT/gradlew -p /path/to/this/probe-source :app:assembleDebug
```

Install the APK on a test emulator/device and enable **GOAT Key Event Probe** in Android accessibility settings. The service requests key filtering but does not retrieve window content. It records only POWER and VOLUME control keys plus screen-state transitions, and returns false so it does not consume keys.

Press Volume Up as a positive control, then press Power three times with the app foreground and background. Clear the log using the on-screen **Clear recorded events** button between phases. The file is `files/events.jsonl`; its contents are also shown on screen. Screen-state candidates are explicitly labeled as guesses with unknown physical-key identity.

The captured run used an unfolded Pixel 9 Pro Fold API 36 emulator. Hardware keys were generated through the emulator controller; `getevent` was an independent test observer, not a dependency of this app. The non-power negative control requested sleep/wake/sleep through system key actions and recorded zero kernel POWER events.

The probe accessibility service was disabled and its original settings restored before the actual GOAT app-mode validation. GOAT does not require the probe or an accessibility service for its screen-event mode.
