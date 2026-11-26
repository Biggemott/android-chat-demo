package com.biggemott.chatdemo.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.biggemott.chatdemo.domain.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ChatDataModule {
    @Binds
    @Singleton
    abstract fun bindChatRepository(repository: RoomChatRepository): ChatRepository

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemUTC()

        @Provides
        @Singleton
        fun provideDatabase(
            @ApplicationContext context: Context,
            clock: Clock,
        ): ChatDatabase = openDemoDatabase(context, clock)

        @Provides
        fun provideMessageDao(database: ChatDatabase): MessageDao = database.messages()

        @Provides
        @Singleton
        fun provideDraftStore(
            @ApplicationContext context: Context,
        ): DraftStore {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val dataStore =
                PreferenceDataStoreFactory.create(scope = scope) {
                    context.preferencesDataStoreFile("chat_draft")
                }
            return PreferencesDraftStore(dataStore, scope)
        }
    }
}
