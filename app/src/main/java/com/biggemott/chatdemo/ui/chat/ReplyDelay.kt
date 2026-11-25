package com.biggemott.chatdemo.ui.chat

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun interface ReplyDelay {
    fun nextDuration(): Duration

    suspend fun await(duration: Duration) {
        delay(duration)
    }
}

private const val MIN_REPLY_DELAY_MS = 1_000
private const val MAX_REPLY_DELAY_MS = 3_000

internal fun randomReplyDelay(random: Random): Duration = random.nextInt(MIN_REPLY_DELAY_MS, MAX_REPLY_DELAY_MS + 1).milliseconds

@Module
@InstallIn(ViewModelComponent::class)
internal object ReplyDelayModule {
    @Provides
    fun provideReplyDelay(): ReplyDelay = ReplyDelay { randomReplyDelay(Random.Default) }
}
