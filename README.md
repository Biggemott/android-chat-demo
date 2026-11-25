# Chat Demo

Chat Demo is a single-module native Android project for an offline conversation between two fixed demo users, Nikita and Sarah. The app currently opens a Material 3 screen showing the app name. The local data layer supports reading and writing messages through a repository.

## Build and test

Use Android Studio or the Gradle wrapper with JDK 25 and Android SDK 37. The minimum Android version is API 26. Dependency and plugin pins are in [the version catalog](gradle/libs.versions.toml).

The project uses Compose BOM 2026.09.00 with compile SDK 37.0. The newer stable BOM 2026.11.01 requires compile SDK 37.1 or later.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

On macOS or Linux, use `./gradlew` in place of `.\gradlew.bat`. The connected tests require a running emulator or connected device. To open the app, select a device and run the `app` configuration in Android Studio.

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

## Testing

JUnit runs local JVM and Android instrumentation tests. The data integration tests use real Room databases, including in-memory databases for isolated tests and temporary files for reopen and rollback cases. They cover sender constraints, chronological ordering, observable updates, clock-based timestamps, seed data, and persistence. Tests use deterministic clocks and bounded waits for Flow emissions.

## Assumptions and limits

The demo is local to a single installation. The intended conversation has exactly two fixed participants. Timestamps are stored at millisecond precision, with generated IDs providing deterministic ordering within the same millisecond.

The launch screen does not display the conversation. Message composition, ViewModel state, and automatic replies are outside the current implementation. There is no account, network delivery, or synchronization. Screenshot testing has not been configured.
