package com.biggemott.chatdemo.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.logging.Logger

internal class PreferencesDraftStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : DraftStore {
    private sealed interface Operation {
        data class Read(
            val result: CompletableDeferred<String>,
        ) : Operation

        data class Write(
            val text: String,
        ) : Operation
    }

    // A restore in a new ViewModel must also wait for edits queued by the previous ViewModel.
    private val operations =
        Channel<Operation>(Channel.UNLIMITED, onUndeliveredElement = { operation ->
            if (operation is Operation.Read) operation.result.cancel()
        })

    init {
        scope
            .launch {
                for (operation in operations) {
                    when (operation) {
                        is Operation.Read -> read(operation.result)
                        is Operation.Write -> write(operation.text)
                    }
                }
            }.invokeOnCompletion { operations.cancel() }
    }

    override suspend fun restore(): String {
        val result = CompletableDeferred<String>()
        try {
            operations.send(Operation.Read(result))
            return result.await()
        } finally {
            result.cancel()
        }
    }

    override fun save(text: String) {
        operations.trySend(Operation.Write(text)).getOrThrow()
    }

    private suspend fun read(result: CompletableDeferred<String>) {
        if (!result.isActive) return
        try {
            result.complete(dataStore.data.first()[draftKey].orEmpty())
        } catch (failure: IOException) {
            result.completeExceptionally(failure)
        } finally {
            result.cancel()
        }
    }

    private suspend fun write(text: String) {
        try {
            dataStore.edit { preferences ->
                if (text.isEmpty()) preferences.remove(draftKey) else preferences[draftKey] = text
            }
        } catch (failure: IOException) {
            logger.warning("Could not save chat draft (${failure.javaClass.simpleName})")
        }
    }

    private companion object {
        val draftKey = stringPreferencesKey("draft")
        val logger: Logger = Logger.getLogger(PreferencesDraftStore::class.java.name)
    }
}
