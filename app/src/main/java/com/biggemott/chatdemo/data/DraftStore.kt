package com.biggemott.chatdemo.data

interface DraftStore {
    suspend fun restore(): String

    fun save(text: String)
}
