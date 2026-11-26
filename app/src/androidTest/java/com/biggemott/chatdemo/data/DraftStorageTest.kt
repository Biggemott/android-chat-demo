package com.biggemott.chatdemo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class DraftStorageTest {
    @get:Rule
    val files = TemporaryFolder()

    @Test
    fun reopensExactDraftAndThenReopensCleared() =
        runBlocking {
            val file = File(files.root, "chat_draft.preferences_pb")
            val draft = "  مرحبا\nAtlas 🗺️  "
            withStore(file) { store ->
                assertEquals("", store.restore())
                store.save("Old edit")
                store.save(draft)
                assertEquals(draft, store.restore())
            }
            withStore(file) { store ->
                assertEquals(draft, store.restore())
                store.save("Another edit")
                store.save("")
                assertEquals("", store.restore())
            }
            withStore(file) { store -> assertEquals("", store.restore()) }
        }

    @Test
    fun delayedFailedWriteCannotPreventNewerEditAndClearFromFinishing() =
        runBlocking {
            val file = File(files.root, "chat_draft.preferences_pb")
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val failFirstWrite: (DataStore<Preferences>) -> DataStore<Preferences> = { dataStore ->
                object : DataStore<Preferences> by dataStore {
                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                        if (started.complete(Unit)) {
                            release.await()
                            throw IOException("Simulated interrupted draft write")
                        }
                        return dataStore.updateData(transform)
                    }
                }
            }
            withStore(file, failFirstWrite) { store ->
                store.save("Old draft")
                started.await()
                store.save("New draft")
                release.complete(Unit)
                assertEquals("New draft", store.restore())
                store.save("")
                assertEquals("", store.restore())
            }
        }

    @Test
    fun cancellingStoreReleasesAnActiveRestore() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val dataStore =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> =
                        flow {
                            started.complete(Unit)
                            awaitCancellation()
                        }

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                        error("No write expected")
                }
            val job = SupervisorJob()
            val store = PreferencesDraftStore(dataStore, CoroutineScope(job + Dispatchers.IO))
            val restore = async { runCatching { store.restore() } }
            try {
                withTimeout(5.seconds) { started.await() }
            } finally {
                job.cancelAndJoin()
            }
            assertTrue(withTimeout(2.seconds) { restore.await() }.exceptionOrNull() is CancellationException)
        }

    private suspend fun withStore(
        file: File,
        wrap: (DataStore<Preferences>) -> DataStore<Preferences> = { it },
        block: suspend (DraftStore) -> Unit,
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            withTimeout(5.seconds) { block(PreferencesDraftStore(wrap(dataStore), scope)) }
        } finally {
            job.cancelAndJoin()
        }
    }
}
