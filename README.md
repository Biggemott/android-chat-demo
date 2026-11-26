# Chat Demo

Chat Demo is a single-module native Android project for an offline conversation between two fixed demo users, Nikita and Sarah. The Compose screen opens at the latest stored message. Nikita can send multiline messages, and Sarah replies from a local script.

## Build and test

Use Android Studio or the Gradle wrapper with JDK 25, Android SDK Platform 37.1 and Build Tools 36.0.0. The app targets Android 17 (API 37) and supports Android 8.0 (API 26) and newer. Dependency and plugin pins are in [the version catalog](gradle/libs.versions.toml).

The project uses stable Compose BOM 2026.11.01 with its minimum compile SDK 37.1. AGP 9.5.2, Gradle 9.6.0, and Kotlin 2.4.21 remain pinned as a tested set.

```powershell
.\gradlew.bat verifyProject
.\gradlew.bat :app:assembleRelease
```

The fast gate runs ktlint, Detekt, Android Lint, JVM tests including screenshot comparisons, and debug assembly. It checks formatting without changing source files. Detekt 1.23.8 runs its CLI against handwritten Kotlin without type resolution. Its stable Android plugin targets AGP 8 and Kotlin 2.0 and does not register a type-aware debug task in this project's AGP 9.5.2 build. On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`. To open the app, select a device and run the `app` configuration in Android Studio.

### Device checks and CI

Run the full connected suite on API 26 and API 37 separately. Use fresh emulators so installation starts with empty app data. The opt-in Gradle property builds the debug APK under an isolated application ID. The startup smoke test checks that ID before opening the real MainActivity with its production Hilt bindings and seeded Room history. Normal debug and release builds keep the standard application ID.

```powershell
$env:ANDROID_SERIAL = "emulator-5554" # Set this to the serial from adb devices.
adb -s $env:ANDROID_SERIAL shell getprop ro.build.version.sdk
$results = Join-Path ([IO.Path]::GetTempPath()) ("chat-connected-" + [guid]::NewGuid())
.\gradlew.bat :app:connectedDebugAndroidTest -PisolatedInstrumentedTests "-PconnectedResultsDir=$results"
if ($LASTEXITCODE -ne 0) { throw "Connected tests failed" }
py -3 .github/scripts/validate_connected.py $results $env:ANDROID_SERIAL
```

On Linux, set `ANDROID_SERIAL` and `EXPECTED_ANDROID_API` and run `bash .github/scripts/run-connected.sh` (Python 3 required). A reused emulator can fail APK installation while Gradle still exits successfully. Check fresh XML for the selected device, at least 35 distinct executed cases, and zero failures, errors, or skips.

In the repository's **Actions** tab, **Fast checks** runs `verifyProject` on Ubuntu 24.04 with Temurin JDK 25, and **Instrumentation** runs independent API 26 and 37 emulator jobs. Both configure the runner's existing Android SDK before using its tools. Both run on pushes and pull requests and support **Run workflow**. Inspect the JVM XML artifact and the validated device counts for the commit under review. Failed runs provide diagnostics. The local IDE inspection workflow is separate.

### Change the app language

On Android 13 and later, open **Settings → System → Languages → App languages → Chat Demo** and choose English or Arabic. On earlier Android versions, change the device language. With no app-specific preference, Android uses the device language preferences and falls back to English resources. The surrounding controls mirror in Arabic. Nikita’s bubbles stay on the physical right and Sarah’s stay on the physical left. The multiline editor and bubbles use each paragraph’s text direction.

### Optional local IDE inspections

Open and sync the project in Android Studio Quail 2 or newer, then wait for indexing. Install the Android CLI and run the commands from the repository root. This workflow was verified with Android Studio Rabbit 2 and Android CLI 1.0.16457483. Use the project name reported as `READY` by `android studio check`.

```powershell
android --version
android studio check
android studio open-file --project=ChatDemo app/src/main/java/com/biggemott/chatdemo/ui/chat/ReplyDelay.kt
android studio analyze-file --project=ChatDemo app/src/main/java/com/biggemott/chatdemo/ui/chat/ReplyDelay.kt
```

Run `analyze-file` for each changed Kotlin, Gradle Kotlin DSL, or Android XML file, including tests. Check the returned path and findings. After external edits, open another project file with `open-file`, reopen the target with the same command, and allow highlighting to settle before retrying. The `Done` response only confirms the open request.

On first use or after an upgrade, verify a known diagnostic before relying on the scan. In `ReplyDelay.kt`, temporarily replace `delay(duration)` with `delay(duration.inWholeMilliseconds)` and check for the legacy-overload warning. Undo only the probe edit, reopen, and confirm the warning disappears. A missed probe, a diagnostic about text that has already changed, or a result for the wrong path makes that run unverified. Retry after reopening and record any unresolved gaps. A zero exit or empty result alone provides no freshness or inspection-coverage guarantee. The CLI reports per-file diagnostics from the running IDE. It provides neither an inspection execution trace nor complete project-wide coverage. This check runs separately from `verifyProject`.

## Architecture

- **Platform and presentation** use Kotlin, Jetpack Compose, Material 3, and a single Android activity.
- **Domain** contains Android-independent users, conversations, conversation memberships, and messages. Inline value classes distinguish user, conversation, and message IDs. A new message carries the conversation, sender, and text. A persisted message also carries its generated ID and an `Instant`.
- **Data** uses Room over SQLite. Hilt supplies the database, repository, and UTC clock, and KSP runs the Hilt and Room processors. Kotlin coroutines and Flow are used for suspending writes and observable reads.

### Local database

| Table | Purpose |
| --- | --- |
| `users` | Stable text ID and display name for each user |
| `conversations` | Stable text ID for each conversation |
| `conversation_participants` | Unique conversation and user pairs, with foreign keys to both tables |
| `messages` | Auto-generated numeric ID, conversation, sender, text, and absolute timestamp in epoch milliseconds |

A composite foreign key from messages to membership enforces valid authorship. The message query filters by conversation and uses an index for ascending timestamp and message ID order. The generated [Room schema](app/schemas/com.biggemott.chatdemo.data.ChatDatabase/1.json) is tracked in Git.

On first creation, the database inserts both users, their conversation and memberships, and eight historical messages in one transaction. Their times are relative to a single clock reading. Later opens preserve the stored data, including an empty message table.

### Reactive repository

`demoChat` supplies the fixed conversation and participant identities. `observeMessages(conversationId)` exposes a `Flow<List<Message>>` backed by Room. The suspending `sendMessage(NewMessage)` accepts nonblank text from either participant, uses the injected clock, and returns the stored record with its generated ID. SQLite enforces membership and write failures propagate to the caller.

### Chat presentation

A Hilt-injected `ChatViewModel` exposes a read-only `StateFlow` with loading, content, or unavailable history and the live draft. It shares one repository observation for the ViewModel lifetime. Draft and typing changes reuse the mapped timeline.

Sending requires loaded history and nonblank text. The ViewModel clears an accepted draft immediately, trims its edges for message storage, and serializes message writes in acceptance order. The timeline shows only Room-observed records. If a write fails, the original text returns to an untouched empty composer. Otherwise, **Recover draft** restores the oldest held failure when the composer is empty. Held failures remain in memory and require explicit recovery and resubmission.

One application-scoped Preferences DataStore saves the visible draft exactly, including whitespace, Unicode, and newlines. Restoration runs asynchronously. Editing or sending before it finishes takes precedence over the stored value. An ordered writer processes edits, clears, and recoveries without a debounce and survives ViewModel destruction. A later restore in the same process waits for queued writes. I/O failures leave the live composer usable and log only the failed operation and exception type.

Each successful outgoing write selects a fresh 1–3 second delay, inclusive, before launching the replacement reply coroutine. The wait begins when the coroutine runs. Outgoing writes and ready replies acquire the same FIFO mutex. At the deadline, the lock holder finishes its write first. A successful outgoing write cancels an older reply that is still queued, delaying, or waiting for the lock. A reply already writing completes first. Typing stays active across replacements. Each reply uses the app language captured when its outgoing send was accepted. Changing language during the wait preserves the pending reply’s language. The English and Arabic scripts share one 21-line sequence that advances only after a reply saves and then cycles. A failed reply leaves the same line available after the next successful send. Stored messages, including the English seed, retain their original text.

The pure Kotlin mapper keeps repository order and uses message IDs as the basis for stable bubble and section keys. Consecutive messages from the same sender group when less than 20 seconds apart. The first message and any gap greater than one hour start a time section. Section headings use the app locale’s weekday and digits and the device zone. The time uses 24-hour `HH:mm` even on a device set to 12-hour time. The screen refreshes its formatter when the app configuration changes, when the device broadcasts a zone change while the screen is active, and when the screen resumes.

The activity collects the ViewModel state with Compose's lifecycle-aware APIs. The screen has separate loading, empty, populated, and unavailable history states. The saveable reverse-layout list opens at the latest message and follows new content or viewport changes when idle and within 40 dp of that edge. Older history keeps its reading anchor and shows a 48 dp **Scroll to latest message** button over the bottom end of the list, above the composer. The list keeps the same size and padding when the button appears, and the button requires enough room for its touch target. An observed outgoing ID takes priority, followed by an explicit button tap, then automatic following. A Compose side effect issues immediate requests against the next list measure. A tall message can satisfy the outgoing request as soon as a portion is visible.

Sarah's typing bubble is a transient list row with a stable key. A persistent polite live region changes when typing starts or ends, even while the row is offscreen. Only the dots' drawing reads animation frames. Compose respects the system animation duration scale and displays static dots at zero. Each message exposes a single speaker-and-text label to accessibility services, and time sections are headings. The editor retains native text editing semantics and a localized Message label. Bubbles wrap to their content and apply their width cap during measurement.

The composer follows system-bar and keyboard insets, opens up to five visible lines, and scrolls internally when text or the available height exceeds that space. Enter inserts a newline and sending keeps focus. In short windows the editor and recovery action take precedence over the header. The composer gives up its outer vertical padding above a tall keyboard to keep a readable line and a full Send target. A compact recovery icon keeps a 48 dp target and the same accessible name. If the status needs more lines than the window can show, it truncates visually and keeps the complete accessibility text. In the shortest windows, the banner returns when the editor and recovery target fit together. The value-based Material 3 field keeps the ViewModel draft as the single text authority and applies restore, clear, and recovery through the same value. Moving to a state-based field on the current pin would require moving that authority into `TextFieldState` and rebuilding the synchronous send boundary and persistence observation. [State-based text fields](https://developer.android.com/develop/ui/compose/text/user-input)

## Testing

JVM tests cover timeline boundaries, authorship, stable keys, localized time labels, and ViewModel transitions. Virtual-time tests exercise draft restoration and edit races, rapid sends, failure recovery, reply replacement across locales, deadline ordering, script cycling, and lifecycle cancellation. Data integration tests use isolated Room and Preferences DataStore files and cover chronological order, observable updates, durability, and draft write failure recovery. Compose instrumentation covers scroll priority, history position across recreation, typing and message semantics, RTL control placement, bidirectional paragraphs, compact recovery at 200% font scale, and time-zone refresh. The test APK uses stable Espresso 3.7.0 for compatibility with Android 17.

Roborazzi on Robolectric checks deterministic images in [the screenshot baseline directory](app/src/test/screenshots). The captures use native graphics, a fixed virtual device, Android API 35, locale, theme, font scale, and UTC seed timestamps. All 11 Windows baselines matched decoded pixels from Ubuntu 24.04 with Temurin 25.0.5.1+2, the fast workflow environment. Private Compose previews sit beside their components and use the same fixtures. Stable Paparazzi 1.3.5 cannot run with this project's AGP 9.5 setup because its plugin requires the removed `BaseExtension`.

```powershell
.\gradlew.bat :app:verifyRoborazziDebug
.\gradlew.bat :app:recordRoborazziDebug
```

Use the record task for an intentional visual change and review the new images before committing them. The verify task compares against the tracked baselines.

## Assumptions and limits

The demo is local to a single installation. The intended conversation has exactly two fixed participants. Timestamps are stored at millisecond precision, with generated IDs providing deterministic ordering within the same millisecond. Moving the device clock backwards can place new messages earlier in the timeline.

The app disables Android Auto Backup and explicitly excludes its database directory and `files/datastore/` directory from legacy backup, cloud backup, and Android device-to-device transfer where the platform applies those rules. Device makers can handle transfer differently. [Android backup behavior](https://developer.android.com/identity/data/autobackup)

Completed draft writes survive a cold launch. Room and DataStore commit independently. Killing the process before a draft edit or send clear reaches disk can restore an earlier draft, including submitted text if Room committed first. A completed clear can also precede a Room write interrupted by process death. Held failed drafts, pending replies, and the script position survive Activity recreation through the ViewModel and reset when it is destroyed. The list saves its index and offset across recreation. A row inserted or removed between saving and the first measure can shift the restored anchor by a row. The floating latest-message button can cover part of a bubble in a short history viewport. The demo has no account, network delivery, or synchronization.
