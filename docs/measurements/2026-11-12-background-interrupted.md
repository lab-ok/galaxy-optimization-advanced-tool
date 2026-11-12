# Interrupted background observation — 2026-11-12

The planned one-hour observation was stopped at the user's request to prioritize the app-level input experiment. This is **not a completed one-hour battery benchmark**.

- Tested code: `5c4c9db0b537c91787ff487a3cd23f4af742fc8c`, the earlier shell-reader implementation.
- Samples collected: 35; last sample at 2040.213 seconds (approximately 34 minutes).
- The emulator reported a constant 100% simulated battery level; this cannot establish actual discharge or zero consumption.
- Early observations showed little GOAT process CPU, but roughly 1.2% of one guest CPU core used by the detached helper group, including its reaped `sleep` children. These are partial observations, not results for the replacement app-event implementation.
- Natural Light Doze and maintenance were observed. Deep Doze was not observed; the image reported no significant-motion sensor. Kernel suspend statistics were inaccessible.
- The collector was interrupted, its files were closed, and the original stay-awake setting was restored. No one-hour result is claimed. The partial raw archive is retained with the local task artifacts.
