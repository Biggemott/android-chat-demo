# Chat Demo engineering guide

The [project overview](../README.md) covers the app, screenshots, and basic setup. This guide describes the implementation and how to reproduce its checks.

## Data and state

### Room schema

| Table | Contents |
| --- | --- |
| `users` | Stable text ID and display name |
| `conversations` | Stable text ID |
| `conversation_participants` | Unique conversation and user pairs, with foreign keys to both tables |
| `messages` | Generated numeric ID, conversation, sender, text, and timestamp in epoch milliseconds |

A composite foreign key from messages to conversation membership enforces valid authorship. The query filters by conversation and uses an index for ascending timestamp and message ID order. The generated [Room schema](../app/schemas/com.biggemott.chatdemo.data.ChatDatabase/1.json) is tracked in Git.

Room creates both users, their conversation and memberships, and eight historical messages in one transaction. Seed timestamps are relative to a single clock reading. Reopening preserves the stored data, including an empty message table.

### Repository and ViewModel

Android-independent domain models use inline value classes for user, conversation, and message IDs. The repository supplies the fixed conversation and participants through `demoChat`. Its `observeMessages(conversationId)` exposes a Room-backed `Flow<List<Message>>`. The suspending `sendMessage(NewMessage)` accepts nonblank text from either participant, timestamps it with the injected UTC clock, and returns the stored message with its generated ID and `Instant`. Storage failures propagate to the caller.

The Hilt-injected `ChatViewModel` collects one history flow for its lifetime and maps each emission with a pure Kotlin mapper. Its read-only `StateFlow` also carries the draft, typing state, and scroll request. Compose collects it with lifecycle awareness and shows loading, empty, populated, or unavailable history. Draft and typing changes reuse the mapped timeline. Visible messages come from the Room observation after persistence.

### Draft persistence and recovery

Send requires loaded history and nonblank text. The ViewModel immediately clears an accepted draft and trims its edges for message storage. When a send fails, it restores the original draft if no newer edit or send intervened and no older failure is waiting. Otherwise it holds the failed text in memory. With an empty composer, **Recover draft** restores the oldest held failure for explicit resubmission.

One application-scoped Preferences DataStore saves the visible draft exactly, including whitespace, Unicode, and newlines. A revision guard gives edits and accepted sends precedence over a late asynchronous restore. A process-scoped writer queues edits, clears, and recoveries in order without a debounce and survives ViewModel destruction. A later restore in the same process waits for queued writes. Draft I/O failures leave the live composer usable. Draft I/O and message write diagnostics record the operation and exception type.

The value-based Material 3 field uses the ViewModel draft for editing, restore, clear, and recovery. Moving to a state-based field would move text ownership to `TextFieldState` and require reworking synchronous send acceptance and draft persistence observation. [Compose text field guide](https://developer.android.com/develop/ui/compose/text/user-input)

### Reply scheduling and concurrency

UI callbacks and reply scheduling run on Main. Outgoing writes enter a FIFO mutex in acceptance order. Each successful outgoing write selects a fresh random delay in the inclusive range of one to three seconds and starts a replacement coroutine. The delay starts when that coroutine runs. After waiting, the reply acquires the same mutex as outgoing writes.

If an outgoing write acquires the mutex first and succeeds, it cancels the previous reply while that reply is queued, delaying, or waiting for the mutex. A reply that has already entered its write finishes first. The typing indicator stays active across replacements. Failed outgoing writes leave an existing pending reply in place.

The reply uses the app language captured when its outgoing send was accepted. English and Arabic share a 21-line script position, which advances only after a reply saves and cycles at the end. A failed reply leaves the same line available for the next successful send. The reply coroutine belongs to the ViewModel and survives activity recreation while that ViewModel is retained.

## Timeline and interaction

### Time and localization

The timeline keeps repository order and derives stable bubble and time section keys from message IDs. Adjacent messages from the same sender group when less than 20 seconds apart. The first message and gaps strictly greater than one hour start time sections. Section headings use the app locale's weekday and digits, and the device time zone. Times use 24-hour `HH:mm` regardless of the device's 12-hour preference. The formatter refreshes on configuration changes, active time-zone broadcasts, and screen resume.

Without an app-specific language preference, Android follows the device language preferences and falls back to English resources. Stored messages, including the English seed, retain their original text. Controls mirror in Arabic, with incoming bubbles on the physical left and outgoing bubbles on the physical right. The editor and bubbles follow each paragraph's direction. The app uses its own Material 3 light and dark palettes and follows the system theme.

### Scrolling and window constraints

The saveable reverse-layout list opens at the latest message. It follows new content and viewport changes while idle and within 40 dp of that edge. When the reader scrolls away, the list preserves its anchor and offers a 48 dp **Scroll to latest message** button over its bottom end when there is room for the touch target. Showing the button leaves the list geometry unchanged. A stored outgoing message observed in the timeline takes priority, followed by a button tap and then automatic following. A Compose side effect requests the scroll on the next list measure. A tall message can satisfy the outgoing scroll request as soon as part of it is visible.

The composer accommodates system bars and the on-screen keyboard. It grows up to five visible lines and scrolls internally when the text or window height requires it. Enter inserts a newline and sending keeps focus. In shorter windows the header yields to the editor and recovery action. The composer drops outer vertical padding above a tall keyboard to preserve one readable line and the full Send target. A compact recovery icon keeps a 48 dp target and the same accessible name. Status text can truncate visually while preserving its full accessibility label. In the shortest windows the banner returns when the editor and recovery target fit together.

### Accessibility semantics and motion

Each message supplies one speaker-and-text label and time section labels are headings. The editor keeps native editing semantics and a localized Message label. Bubbles wrap to their content and apply the width cap during measurement.

The typing bubble is a transient list row with a stable key. A persistent polite live region reports typing changes even while that row is offscreen. Only the dots' drawing reads animation frames. The dots respect the system animation duration scale and stay static at zero.

## Build tools and retained warnings

Use the SDK and JDK from [Getting started](../README.md#getting-started). AGP 9.5.2, Gradle 9.6.0, and Kotlin 2.4.21 are pinned as a tested set. Compose BOM 2026.11.01 requires compile SDK 37.1. The stable Espresso 3.7.0 constraint avoids reflection on an input API removed in Android 17. The Lifecycle runtime constraint avoids older transitive lint checks that use obsolete lint APIs. Versions are in the [catalog](../gradle/libs.versions.toml).

Detekt 1.23.8 runs its CLI on handwritten Kotlin without type resolution. The stable Gradle plugin targets AGP 8 and Kotlin 2.0 and does not register a type-aware debug task on this build. Roborazzi runs with Robolectric. Paparazzi 1.3.5 requires Gradle's removed Android `BaseExtension` API and is incompatible with this AGP setup.

- Android Lint's `AndroidGradlePluginVersion` advisory reflects the deliberate AGP pin. The `UnusedAttribute` warning for `android:localeConfig` reflects the API 26 minimum. Android reads the attribute for per-app language preferences from API 33, and earlier releases use the device language.
- Android Studio flags incubating Gradle APIs in the Detekt configuration and repository policy. The pinned Gradle build exercises those APIs. Its manifest warning has the same API-level rationale as Lint. The repeated Compose BOM inspection is informational and refers to distinct production, screenshot, and instrumentation configurations.
- Detekt's CLI emits a JDK warning because its bundled Kotlin compiler uses a deprecated `sun.misc.Unsafe` method. The analyzer completes on the pinned JDK.
- Detekt suppressions are local. The seed keeps timing literals next to the dialogue they pace. The message write boundary catches storage exceptions with different base types and explicitly rethrows coroutine cancellation.

## Verification

### JVM and screenshots

Run from the repository root. On macOS or Linux use `./gradlew` instead of `.\gradlew.bat`.

```powershell
.\gradlew.bat verifyProject
.\gradlew.bat :app:verifyRoborazziDebug
.\gradlew.bat :app:assembleRelease
```

The fast gate checks formatting without editing source files. JVM tests cover timeline boundaries, stable keys, localized timestamps, and ViewModel transitions. Virtual time exercises draft races, rapid sends, failure recovery, deadline ordering, reply replacement across locales, script cycling, and cancellation.

The [screenshot baselines](../app/src/test/screenshots) use native graphics and fixed API 35 device, locale, theme, font scale, and UTC time inputs per fixture. The parity check compared decoded pixels on Windows with JBR 25.0.4 and Ubuntu 24.04 with Temurin 25.0.5.1+2. Private Compose previews sit beside their components and share fixtures. Run `./gradlew :app:recordRoborazziDebug` for an intentional visual change and inspect the images before committing them. The verification task compares the rendered UI against the tracked baselines.

### Device checks and CI

Run the full connected suite separately on fresh API 26 and API 37 emulators. For API 37, CI uses the 16 KB system image `system-images;android-37.2;google_apis_ps16k;x86_64`. The opt-in Gradle property adds the `.instrumentation` suffix to the debug application ID. The production startup smoke test checks that ID before launching the real activity through Hilt and observing the Room seed. Ordinary debug and release builds use the standard ID. Data integration tests isolate Room and Preferences DataStore. Compose cases exercise real Room sends and replies, scrolling and recreation, RTL and compact layout, semantics, and time-zone refresh.

On Windows, set the serial from `adb devices` and set `$expectedApi` for each run. Use Python 3 to validate the fresh result directory.

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
$expectedApi = "26" # Change to "37" for the target-API run.
$actualApi = adb -s $env:ANDROID_SERIAL shell getprop ro.build.version.sdk
if ($LASTEXITCODE -ne 0 -or $actualApi -ne $expectedApi) { throw "Unexpected emulator API" }
if ($expectedApi -eq "37") {
    $pageSize = adb -s $env:ANDROID_SERIAL shell getconf PAGE_SIZE
    if ($LASTEXITCODE -ne 0 -or $pageSize -ne "16384") { throw "Expected 16 KB runtime pages" }
}
$results = Join-Path ([IO.Path]::GetTempPath()) ("chat-connected-" + [guid]::NewGuid())
.\gradlew.bat :app:connectedDebugAndroidTest -PisolatedInstrumentedTests "-PconnectedResultsDir=$results"
if ($LASTEXITCODE -ne 0) { throw "Connected tests failed" }
py -3 .github/scripts/validate_connected.py $results $env:ANDROID_SERIAL
if ($LASTEXITCODE -ne 0) { throw "Connected result validation failed" }
```

On Linux, set `ANDROID_SERIAL` and `EXPECTED_ANDROID_API`, and set `EXPECTED_ANDROID_PAGE_SIZE=16384` for API 37 to match CI. Run `bash .github/scripts/run-connected.sh` with Python 3 available. The validator checks fresh XML for the selected device, at least 35 distinct executed cases, and zero failures, errors, or skips. An APK installation failure can otherwise leave Gradle returning success with zero executed cases.

Both GitHub Actions workflows run on pushes and pull requests and support manual runs. Fast checks uses Ubuntu 24.04 and Temurin JDK 25 and uploads JVM XML. Instrumentation runs one job per API and reports the validated counts in the job log. Both upload failure diagnostics. [Fast checks workflow](../.github/workflows/fast.yml) · [Instrumentation workflow](../.github/workflows/instrumentation.yml)

### Runtime checks

A bounded API 37 emulator check used a minified release APK signed with a disposable key. It exercised production startup, send and reply, restoration of committed history and a completed draft after process death, and Home or sleep followed by return while the process was still alive. It also checked Gboard multiline entry and editing, system bar, cutout and keyboard insets, and configuration changes involving orientation, font scale, theme, and locale.

On the same emulator, a debug run with 1,000 total messages, including the eight seed messages, exercised initial positioning, reading older history while a reply arrived, jumping back to the latest message, sending, and cold reopen. These exploratory checks were run outside CI on one emulator configuration. Manual TalkBack speech, gestures, and focus checks are pending.

### Optional local IDE inspections

Open and sync the project in Android Studio Quail 2 or newer and wait for indexing. Install the Android CLI and run the commands below from the repository root. The workflow was verified with Android Studio Rabbit 2 and Android CLI 1.0.16457483. Use the project name shown as `READY` by `android studio check`.

```powershell
android --version
android studio check
android studio open-file --project=ChatDemo app/src/main/java/com/biggemott/chatdemo/ui/chat/ReplyDelay.kt
android studio analyze-file --project=ChatDemo app/src/main/java/com/biggemott/chatdemo/ui/chat/ReplyDelay.kt
```

Run `analyze-file` for each changed Kotlin source, test, Gradle Kotlin DSL file, or Android XML file and verify the returned path. After external edits, open another project file with `open-file`, reopen the target with the same command, and allow highlighting to settle before retrying. The `Done` response confirms the open request.

On first use or after an upgrade, verify a known diagnostic. In `ReplyDelay.kt`, temporarily replace `delay(duration)` with `delay(duration.inWholeMilliseconds)` and confirm that the legacy-overload warning appears. Undo only the probe edit, reopen the file, and confirm the warning disappears. Retry after reopening if the warning is missing, if it refers to old text, or if the result shows the wrong path. Record unresolved gaps. A zero exit code or empty result alone gives no guarantee of freshness. The CLI returns per-file diagnostics from the running IDE, with no execution trace or project-wide coverage guarantee. This workflow runs separately from the fast gate.

## Storage and layout limits

- Room loads the full conversation and the ViewModel maps it on history emissions. There is no pagination. Exceptionally large individual messages can exceed Android database cursor or rendering limits.
- Timestamps use wall-clock milliseconds with generated IDs as tie-breakers. If history observation fails, the app keeps the draft and shows an unavailable state. Observation restarts when a new ViewModel is created.
- Room and DataStore write independently. Process death before a draft edit or clear reaches disk can restore an earlier value, including submitted text if Room committed first. A completed clear can also precede an interrupted Room write. Held failed drafts, pending replies, and the script position are in memory for the lifetime of the ViewModel.
- Android Auto Backup is disabled. Backup rules exclude the database directory and `files/datastore/` from legacy backup, cloud backup, and device transfer where Android applies the rules. Device manufacturers can handle transfer differently. [Android backup behavior](https://developer.android.com/identity/data/autobackup)
- Scroll restoration saves the list index and offset. An insertion or removal before the first measure can shift the restored anchor by a row. The floating latest-message button can cover part of a bubble in short viewports.
