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

A Hilt-injected `ChatViewModel` exposes a read-only `StateFlow` with loading, content, or unavailable history and an in-memory draft. It shares one repository observation for the ViewModel lifetime. Draft and typing changes reuse the mapped timeline.

Sending requires loaded history and nonblank text. The ViewModel clears an accepted draft immediately, trims its edges for storage, and serializes writes in acceptance order. The timeline shows only Room-observed records. The screen scrolls to the latest requested outgoing ID when that record appears in the timeline. If a write fails, the original text returns to an untouched empty composer. Otherwise, **Recover draft** restores the oldest held failure when the composer is empty. Failed drafts remain in memory and require explicit resubmission.

Each successful outgoing write replaces the pending reply with a freshly selected 1–3 second delay, inclusive. Outgoing writes and ready replies acquire the same FIFO mutex. At the deadline, the lock holder finishes its write first. A successful outgoing write cancels an older reply that is still delaying or waiting for the lock. A reply already writing completes first. Typing stays active across replacements. The 21-line script advances only when a reply is saved, then cycles at the end. When a reply fails to save, typing ends and the same line is available after the next successful send.

The pure Kotlin mapper keeps repository order and uses message IDs as the basis for stable bubble and section keys. Consecutive messages from the same sender group when less than 20 seconds apart. The first message and any gap greater than one hour start a time section. Section labels use a caller-supplied locale and time zone, so they can update after a configuration change.

The activity collects the ViewModel state with Compose's lifecycle-aware APIs. The screen has separate loading, empty, populated, and unavailable history states. A saveable, bottom-anchored lazy list places short histories just above the composer and follows a new reply when already at the bottom. The history reuses its formatter for the current locale and time-zone ID. Bubbles wrap to their content and apply their width cap during measurement. Message sides stay fixed across layout directions. The initial avatar, grouped gray and pink bubbles, and light and dark Material 3 palettes use app resources and system typography. The composer uses one to five visible lines and follows system-bar and keyboard insets.

## Testing

JVM tests cover timeline boundaries, authorship, stable keys, time labels, and ViewModel transitions. Virtual-time tests exercise rapid sends, failure recovery, reply replacement, deadline ordering, script cycling, and lifecycle cancellation. Data integration tests use real Room databases and cover sender constraints, chronological ordering, observable updates, clock-based timestamps, seed data, and persistence. Compose instrumentation uses a fresh seeded Room database, the actual ViewModel, and the production route. It checks outgoing scroll from older history, both persisted messages, and a pending reply across Activity recreation. A short fixture checks bubble alignment, order, and spacing. The test APK uses stable Espresso 3.7.0 for compatibility with Android 17.

Roborazzi on Robolectric checks deterministic images in [the screenshot baseline directory](app/src/test/screenshots). The captures use a fixed virtual device, Android API, locale, theme, font scale, and UTC seed timestamps. Private Compose previews sit beside their components and use the same fixtures. Stable Paparazzi 1.3.5 cannot run with this project's AGP 9.5 setup because its plugin requires the removed `BaseExtension`.

```powershell
.\gradlew.bat :app:verifyRoborazziDebug
.\gradlew.bat :app:recordRoborazziDebug
```

Use the record task for an intentional visual change and review the new images before committing them. The verify task compares against the tracked baselines.

## Assumptions and limits

The demo is local to a single installation. The intended conversation has exactly two fixed participants. Timestamps are stored at millisecond precision, with generated IDs providing deterministic ordering within the same millisecond. Moving the device clock backwards can place new messages earlier in the timeline.

The app disables Android Auto Backup and explicitly excludes its database directory from legacy backup, cloud backup, and Android device-to-device transfer where the platform applies those rules. Device makers can handle transfer differently. [Android backup behavior](https://developer.android.com/identity/data/autobackup)

Drafts, failed text, and pending replies survive Activity recreation through the ViewModel. They are lost when the ViewModel is destroyed, and the in-memory script position resets. A message already committed to Room remains stored if its coroutine is later cancelled. Persistent drafts, full smart scrolling, animated typing, and Arabic UI strings are future work. The demo has no account, network delivery, or synchronization.
