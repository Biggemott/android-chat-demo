# Chat Demo

Chat Demo is a single-module native Android project for an offline conversation between two fixed demo users, Nikita and Sarah. The Compose screen opens the Room-backed conversation at the latest message and lets you edit a multiline draft.

## Build and test

Use Android Studio or the Gradle wrapper with JDK 25 and Android SDK 37. The minimum Android version is API 26. Dependency and plugin pins are in [the version catalog](gradle/libs.versions.toml).

The project uses Compose BOM 2026.09.00 with compile SDK 37.0. The newer stable BOM 2026.11.01 requires compile SDK 37.1 or later.

```powershell
.\gradlew.bat verifyProject
.\gradlew.bat connectedDebugAndroidTest
```

The fast gate runs ktlint, Detekt, Android Lint, JVM tests including screenshot comparisons, and debug assembly. It checks formatting without changing source files. Detekt runs the stable CLI against handwritten Kotlin with its own compiler and without type resolution. On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`. The connected tests require a running emulator or connected device. To open the app, select a device and run the `app` configuration in Android Studio.

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

A Hilt-injected `ChatViewModel` exposes a read-only `StateFlow` with loading, content, or unavailable history and an in-memory draft. It shares one repository observation for the ViewModel lifetime. Editing the draft preserves the input verbatim and reuses the mapped timeline.

The pure Kotlin mapper keeps repository order and uses message IDs as the basis for stable bubble and section keys. Consecutive messages from the same sender group when less than 20 seconds apart. The first message and any gap greater than one hour start a time section. Section labels use a caller-supplied locale and time zone, so they can update after a configuration change.

The activity collects the ViewModel state with Compose's lifecycle-aware APIs. The screen has separate loading, empty, populated, and unavailable history states. A saveable, bottom-anchored lazy list places short histories just above the composer and keeps the latest messages in view as the viewport shrinks. The history reuses its formatter for the current locale and time-zone ID. Bubbles wrap to their content and apply their width cap during measurement. Message sides stay fixed across layout directions. The initial avatar, grouped gray and pink bubbles, and light and dark Material 3 palettes use app resources and system typography. The composer uses one to five visible lines and follows system-bar and keyboard insets.

## Testing

JVM tests cover timeline boundaries, authorship, stable keys, time labels, and ViewModel transitions using a controlled coroutine scheduler. Data integration tests use real Room databases and cover sender constraints, chronological ordering, observable updates, clock-based timestamps, seed data, and persistence. Compose instrumentation opens the real seeded chat to check the latest message, sections, editing, and the disabled send control. A short fixture checks bubble alignment, order, and relative spacing without depending on how much history fits on a device. The test APK uses stable Espresso 3.7.0 for compatibility with Android 17.

Roborazzi on Robolectric checks five deterministic images in [the screenshot baseline directory](app/src/test/screenshots). They cover seeded light and dark chat, empty history, an enabled multiline composer, and large text with an emoji. The captures use a fixed virtual device, Android API, locale, theme, font scale, and UTC seed timestamps. Debug-only Compose previews use the same fixtures. Stable Paparazzi 1.3.5 cannot run with this project's AGP 9.5 setup because its plugin requires the removed `BaseExtension`.

```powershell
.\gradlew.bat :app:verifyRoborazziDebug
.\gradlew.bat :app:recordRoborazziDebug
```

Use the record task for an intentional visual change and review the new images before committing them. The verify task compares against the tracked baselines.

## Assumptions and limits

The demo is local to a single installation. The intended conversation has exactly two fixed participants. Timestamps are stored at millisecond precision, with generated IDs providing deterministic ordering within the same millisecond. Moving the device clock backwards can place new messages earlier in the timeline.

The app disables Android Auto Backup and explicitly excludes its database directory from legacy backup, cloud backup, and Android device-to-device transfer where the platform applies those rules. Device makers can handle transfer differently. [Android backup behavior](https://developer.android.com/identity/data/autobackup)

The draft is held in memory. The live send control is disabled until message submission is wired to the repository. Automatic replies, persistent drafts, adaptive incoming-message scrolling, and Arabic UI strings are future work. The demo has no account, network delivery, or synchronization.
