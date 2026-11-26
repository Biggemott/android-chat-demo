# Chat Demo

Chat Demo is a single-module native Android project for an offline conversation between two fixed demo users, Nikita and Sarah. The Compose screen opens at the latest stored message. Nikita can send multiline messages, and Sarah replies from a local script.

## Build and test

Use Android Studio or the Gradle wrapper with JDK 25 and Android SDK 37. The minimum Android version is API 26. Dependency and plugin pins are in [the version catalog](gradle/libs.versions.toml).

The project uses Compose BOM 2026.09.00 with compile SDK 37.0. The newer stable BOM 2026.11.01 requires compile SDK 37.1 or later.

```powershell
.\gradlew.bat verifyProject
.\gradlew.bat connectedDebugAndroidTest
```

The fast gate runs ktlint, Detekt, Android Lint, JVM tests including screenshot comparisons, and debug assembly. It checks formatting without changing source files. Detekt runs the stable CLI against handwritten Kotlin with its own compiler and without type resolution. On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`. The connected tests require a running emulator or connected device. To open the app, select a device and run the `app` configuration in Android Studio.

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

Each successful outgoing write selects a fresh 1–3 second delay, inclusive, before launching the replacement reply coroutine. The wait begins when the coroutine runs. Outgoing writes and ready replies acquire the same FIFO mutex. At the deadline, the lock holder finishes its write first. A successful outgoing write cancels an older reply that is still queued, delaying, or waiting for the lock. A reply already writing completes first. Typing stays active across replacements. The 21-line script advances only when a reply is saved, then cycles at the end. When a reply fails to save, typing ends and the same line is available after the next successful send.

The pure Kotlin mapper keeps repository order and uses message IDs as the basis for stable bubble and section keys. Consecutive messages from the same sender group when less than 20 seconds apart. The first message and any gap greater than one hour start a time section. Section labels use a caller-supplied locale and time zone, so they can update after a configuration change.

The activity collects the ViewModel state with Compose's lifecycle-aware APIs. The screen has separate loading, empty, populated, and unavailable history states. The saveable reverse-layout list opens at the latest message and follows new content or viewport changes when within about 40 dp of that edge and idle. Older history keeps its reading anchor and shows a 48 dp **Scroll to latest message** button above the composer. In short windows the button moves beside the list to preserve its height. An observed outgoing ID takes priority, followed by an explicit button tap, then automatic following. Jumps are immediate. A tall message can satisfy an outgoing request as soon as part of it is on screen.

Sarah's typing bubble is a transient list row with a stable key. A persistent polite live region changes when typing starts or ends, even while the row is offscreen. Only the dots' drawing reads animation frames. Compose respects the system animation duration scale and displays static dots at zero. The history reuses its formatter for the current locale and time-zone ID. Bubbles wrap to their content and apply their width cap during measurement. Message sides stay fixed across layout directions.

The composer follows system-bar and keyboard insets, opens up to five visible lines, and scrolls internally when text or the available height exceeds that space. Enter inserts a newline and sending keeps focus. In very short windows, the header and any recovery banner yield space to the editor. Held drafts remain recoverable when the window has room for the banner again. The value-based Material 3 field keeps the ViewModel draft as the single text authority and applies restore, clear, and recovery through the same value. Moving to a state-based field on the current pin would require moving that authority into `TextFieldState` and rebuilding the synchronous send boundary and persistence observation. [State-based text fields](https://developer.android.com/develop/ui/compose/text/user-input)

## Testing

JVM tests cover timeline boundaries, authorship, stable keys, time labels, and ViewModel transitions. Virtual-time tests exercise draft restoration and edit races, rapid sends, failure recovery, reply replacement, deadline ordering, script cycling, and lifecycle cancellation. Data integration tests use isolated Room and Preferences DataStore files and cover chronological order, observable updates, durability, and draft write failure recovery. Compose instrumentation covers scroll priority, history position across recreation, the bottom button, typing semantics, long sends, and viewport changes. The test APK uses stable Espresso 3.7.0 for compatibility with Android 17.

Roborazzi on Robolectric checks deterministic images in [the screenshot baseline directory](app/src/test/screenshots). The captures use a fixed virtual device, Android API, locale, theme, font scale, and UTC seed timestamps. Private Compose previews sit beside their components and use the same fixtures. Stable Paparazzi 1.3.5 cannot run with this project's AGP 9.5 setup because its plugin requires the removed `BaseExtension`.

```powershell
.\gradlew.bat :app:verifyRoborazziDebug
.\gradlew.bat :app:recordRoborazziDebug
```

Use the record task for an intentional visual change and review the new images before committing them. The verify task compares against the tracked baselines.

## Assumptions and limits

The demo is local to a single installation. The intended conversation has exactly two fixed participants. Timestamps are stored at millisecond precision, with generated IDs providing deterministic ordering within the same millisecond. Moving the device clock backwards can place new messages earlier in the timeline.

The app disables Android Auto Backup and explicitly excludes its database directory and `files/datastore/` directory from legacy backup, cloud backup, and Android device-to-device transfer where the platform applies those rules. Device makers can handle transfer differently. [Android backup behavior](https://developer.android.com/identity/data/autobackup)

Completed draft writes survive a cold launch. Room and DataStore commit independently. Killing the process before a draft edit or send clear reaches disk can restore an earlier draft, including submitted text if Room committed first. A completed clear can also precede a Room write interrupted by process death. Held failed drafts, pending replies, and the script position survive Activity recreation through the ViewModel and reset when it is destroyed. The list saves its index and offset across recreation. A row inserted or removed between saving and the first measure can shift the restored anchor by a row. The demo has no account, network delivery, or synchronization. Arabic UI strings are future work.
