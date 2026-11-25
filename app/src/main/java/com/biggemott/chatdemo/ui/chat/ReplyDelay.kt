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

internal fun randomReplyDelayMillis(random: Random): Long = random.nextInt(1_000, 3_001).toLong()

@Module
@InstallIn(ViewModelComponent::class)
internal object ReplyDelayModule {
    @Provides
    fun provideReplyDelay(): ReplyDelay = ReplyDelay { delay(randomReplyDelayMillis(Random.Default)) }
}
