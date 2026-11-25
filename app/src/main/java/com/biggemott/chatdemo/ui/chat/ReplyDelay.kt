package com.biggemott.chatdemo.ui.chat

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import kotlinx.coroutines.delay
import kotlin.random.Random

fun interface ReplyDelay {
    suspend fun await()
}

private const val MIN_REPLY_DELAY_MS = 1_000
private const val MAX_REPLY_DELAY_MS = 3_000

internal fun randomReplyDelayMillis(random: Random): Long = random.nextInt(MIN_REPLY_DELAY_MS, MAX_REPLY_DELAY_MS + 1).toLong()

@Module
@InstallIn(ViewModelComponent::class)
internal object ReplyDelayModule {
    @Provides
    fun provideReplyDelay(): ReplyDelay = ReplyDelay { delay(randomReplyDelayMillis(Random.Default)) }
}
