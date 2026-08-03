# Sources

What was consulted while building this, and when. The point is that you can tell
later what was current at the time, and which claims rest on a document I actually
read versus one I only saw summarised.

---

## Session 1 — 2026-07-30 — speech capability probe

### Read directly

| Source | Last updated (as stated by the page) | Used for |
|---|---|---|
| [Voice input \| Wear OS](https://developer.android.com/training/wearables/user-input/voice) | **2024-11-12** | Google's recommended path for speech on Wear OS |
| [Debug a Wear OS app](https://developer.android.com/training/wearables/get-started/debugging) | **2026-06-23** | Bluetooth debugging removed in Wear OS 3 |
| [Debug Wear OS over Wi-Fi](https://developer.android.com/training/wearables/get-started/debug-wifi) | **2026-06-23** | The pairing/connection steps in `WATCH_SETUP.md` |
| [Connect Galaxy Watch to Android Studio over Wi-Fi](https://developer.samsung.com/sdp/blog/en-us/2024/04/30/connect-galaxy-watch-to-android-studio-over-wi-fi) (Samsung) | published **2024-04-30** | Samsung menu paths; the "Turn off automatic Wi-Fi" setting |

**Note on the voice input page:** it is nearly two years old, recommends the
`ACTION_RECOGNIZE_SPEECH` intent, and says **nothing at all** about on-device
recognition, `EXTRA_PREFER_OFFLINE`, `createOnDeviceSpeechRecognizer`, permissions,
or offline operation. Google's Wear OS speech documentation does not cover the
case this project needs. That gap is why the API facts below were taken from the
SDK rather than from the docs.

### Read from the installed SDK, not the web

Reference pages on `developer.android.com` are rendered client-side, so fetching
them returns the navigation shell rather than the content. These facts were read
straight out of the SDK instead, which is authoritative and version-exact:

- `platforms/android-37.0/android.jar` — via `javap`, for method signatures and the
  exact numeric values of every `SpeechRecognizer.ERROR_*` constant
- `platforms/android-37.0/data/api-versions.xml` — for the API level each member was
  added in

Established this way:

| API | Added in |
|---|---|
| `createOnDeviceSpeechRecognizer`, `isOnDeviceRecognitionAvailable` | **31** |
| `checkRecognitionSupport`, `RecognitionSupport`, `triggerModelDownload(Intent)`, `EXTRA_BIASING_STRINGS` | **33** |
| `triggerModelDownload` with `ModelDownloadListener` | **34** |
| `EXTRA_PREFER_OFFLINE` | **23** |

`RecognitionSupport` reports four separate lists — `getInstalledOnDeviceLanguages`,
`getSupportedOnDeviceLanguages`, `getPendingOnDeviceLanguages` and
`getOnlineLanguages` — which is what makes a meaningful offline-capability probe
possible at all.

### Read from Maven metadata, not from documentation

Every dependency version was taken from the artifact's own `maven-metadata.xml` on
2026-07-30, rather than from a release-notes page or from memory:

| Component | Pinned | Newest stable at the time |
|---|---|---|
| AGP | 9.3.1 | 9.3.1 |
| Gradle | 9.5.0 | (wrapper, carried over) |
| Kotlin | 2.4.10 | 2.4.10 |
| Compose BOM | 2026.06.01 | 2026.06.01 |
| `androidx.wear.compose` | 1.6.2 | 1.6.2 |
| `androidx.core:core-ktx` | 1.19.0 | 1.19.0 |
| `androidx.activity:activity-compose` | 1.13.0 | 1.13.0 |
| `androidx.wear:wear-tooling-preview` | 1.0.0 | 1.0.0 |

No pre-release versions were taken. AGP 9.4.0-alpha07, Kotlin 2.4.20-Beta2 and
Wear Compose 1.7.0-alpha07 all existed and were all deliberately skipped.

SDK levels: `compileSdk = 37`, `targetSdk = 36`, `minSdk = 34`. See
`app/build.gradle.kts` for why minSdk is 34 rather than lower.

### Seen only in search summaries — NOT verified

Recorded because they shaped expectations, and flagged because they were not read
in full. Do not treat these as established:

- **"Speech Recognition & Synthesis (Wear OS)"** appears to be a real Google package
  shipped on Wear OS (APKMirror lists a build dated 2026-04-27) that provides
  speech-to-text to third-party apps. Whether it registers as an **on-device**
  recognizer is exactly what the probe exists to determine.
- Multiple developer reports that Wear OS **emulator** images carry no recognition
  service, and that `SpeechRecognizer` fails there with "no selected voice
  recognition service". **This has now been confirmed first-hand** — see below.
- [Vosk](https://alphacephei.com/vosk/android) — offline recognition toolkit,
  ~50 MB models, supports grammar-constrained vocabularies. The fallback if the
  platform's on-device path proves unusable. Not evaluated yet.

### Confirmed by running it

On the `Wear5_Round` emulator (Wear OS 5 / API 34), 2026-07-30:

- microphone: declared present
- `isRecognitionAvailable`: **false**
- `isOnDeviceRecognitionAvailable`: **false**
- installed recognition services: **none**

So the emulator can validate the build, the UI and the failure paths, but it
cannot answer the question this project actually needs answered. That requires
the physical watch.

---

## Findings on the actual watch — 2026-07-30

Galaxy Watch 7 40 mm, `SM-L300`, paired over Wi-Fi and probed directly.

### Device facts (measured, not from spec sheets)

| | Reported by the device | Note |
|---|---|---|
| Android | **16 — API 36** | i.e. **Wear OS 6**, not 5 |
| One UI Watch | 80000 (8.0) | |
| Security patch | 2026-07-05 | |
| Screen | **432 x 432 px @ 340 dpi** | ≈ **203 dp** wide |

**Both of the assumptions carried over from the HelloWorld session were wrong.**
The watch is on Wear OS 6, and the screen is 432x432, not the 396x396 that spec
sheets report. Layout work must use 203 dp, not 198.

### Speech recognition — the decisive result

| Probe | Result |
|---|---|
| microphone | present |
| `isRecognitionAvailable` | **true** |
| `isOnDeviceRecognitionAvailable` | **false** |
| recognition services installed | exactly one |
| `checkRecognitionSupport` | **ERROR_CANNOT_CHECK_SUPPORT (14)** |

The single service is
`com.google.android.tts/…GoogleTTSRecognitionService`, build
`googletts.google-speech-apk_20260618.00_p1-wear`, which is also the system
default. No AICore, no ML Kit GenAI, no Samsung package registering as a
`RecognitionService` (Bixby and `intellivoiceservice` are present but do not).

**Conclusion: `createOnDeviceSpeechRecognizer()` is not viable on this hardware.**
The watch has exactly one recognizer, it is not registered as an on-device
recognizer, and it refuses even to report which languages it supports.

### Still untested

Whether the *default* recognizer, given `EXTRA_PREFER_OFFLINE`, works with the
watch fully offline. Not run here because doing so while the watch is online would
send audio to Google — which the project forbids. The test must be run in airplane
mode, and cannot be driven over ADB because ADB is itself the network connection.
