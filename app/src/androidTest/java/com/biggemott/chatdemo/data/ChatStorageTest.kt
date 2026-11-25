package com.biggemott.chatdemo.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.biggemott.chatdemo.domain.ConversationId
import com.biggemott.chatdemo.domain.NewMessage
import com.biggemott.chatdemo.domain.UserId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChatStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-01-18T18:04:08.123456Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val storedNow = Instant.ofEpochMilli(now.toEpochMilli())
    private val chat = ConversationId("test")
    private val otherChat = ConversationId("another-test")
    private val nikita = UserId("nikita")
    private val sarah = UserId("sarah")
    private lateinit var database: ChatDatabase
    private lateinit var repository: RoomChatRepository
    private val sqlite get() = database.openHelper.writableDatabase

    @Before
    fun openEmptyDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        repository = RoomChatRepository(database.messages(), clock)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun persistsBothSendersWithDistinctIdsAndTimestampFromClock() =
        runBlocking {
            prepareConversation()
            val outgoing = repository.sendMessage(NewMessage(chat, nikita, "Hello Sarah"))
            val incoming = repository.sendMessage(NewMessage(chat, sarah, "Hello Nikita"))
            val stored = withTimeout(5_000) { repository.observeMessages(chat).first() }

            assertNotEquals(outgoing.id, incoming.id)
            assertTrue(outgoing.id.value > 0)
            assertEquals(listOf(outgoing, incoming), stored)
            assertEquals(listOf(nikita, sarah), stored.map { it.senderId })
            assertEquals(listOf(chat, chat), stored.map { it.conversationId })
            assertEquals(listOf("Hello Sarah", "Hello Nikita"), stored.map { it.text })
            assertEquals(listOf(storedNow, storedNow), stored.map { it.sentAt })
        }

    @Test
    fun ordersByTimestampThenIdAndFiltersByConversation() =
        runBlocking {
            prepareConversation()
            val dao = database.messages()
            val lateId =
                dao.insert(
                    MessageEntity(conversationId = chat.value, senderId = nikita.value, text = "late", sentAtEpochMs = 3000),
                )
            val earlyId =
                dao.insert(
                    MessageEntity(conversationId = chat.value, senderId = sarah.value, text = "early", sentAtEpochMs = 1000),
                )
            val otherId =
                dao.insert(
                    MessageEntity(conversationId = otherChat.value, senderId = sarah.value, text = "other", sentAtEpochMs = 1000),
                )
            val tiedId =
                dao.insert(
                    MessageEntity(conversationId = chat.value, senderId = nikita.value, text = "tied", sentAtEpochMs = 1000),
                )

            withTimeout(5_000) {
                assertEquals(listOf(earlyId, tiedId, lateId), repository.observeMessages(chat).first().map { it.id.value })
                assertEquals(listOf(otherId), repository.observeMessages(otherChat).first().map { it.id.value })
            }
        }

    @Test
    fun rejectsInvalidForeignKeysAndDuplicateMembership() {
        prepareConversation()
        assertThrows(SQLiteConstraintException::class.java) {
            insertParticipant(chat.value, nikita.value)
        }
        assertThrows(SQLiteConstraintException::class.java) {
            insertParticipant(chat.value, "missing-user")
        }
        assertThrows(SQLiteConstraintException::class.java) {
            insertParticipant("missing-chat", nikita.value)
        }
        sqlite.execSQL("INSERT INTO users (id, display_name) VALUES ('visitor', 'Visitor')")
        for ((conversation, sender) in listOf(
            "missing-chat" to nikita.value,
            chat.value to "missing-user",
            chat.value to "visitor",
        )) {
            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking { repository.sendMessage(NewMessage(ConversationId(conversation), UserId(sender), "Invalid")) }
            }
        }
        assertEquals(0, scalar(sqlite, "SELECT COUNT(*) FROM messages"))
    }

    @Test
    fun emitsInitiallyEmptyThenUpdatesAfterInsert() =
        runBlocking {
            prepareConversation()
            var emissions = 0
            withTimeout(5_000) {
                repository.observeMessages(chat).first { messages ->
                    if (emissions++ == 0) {
                        assertTrue(messages.isEmpty())
                        repository.sendMessage(NewMessage(chat, sarah, "An update"))
                        false
                    } else {
                        assertEquals(listOf("An update"), messages.map { it.text })
                        true
                    }
                }
            }
            assertEquals(2, emissions)
        }

    @Test
    fun rejectsWhitespaceOnlyInput() {
        prepareConversation()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.sendMessage(NewMessage(chat, nikita, " \t\n ")) }
        }
        assertEquals(0, scalar(sqlite, "SELECT COUNT(*) FROM messages"))
    }

    @Test
    fun firstObservationIncludesCompleteSeedAndReopenPreservesNewMessage() =
        runBlocking {
            withDatabaseName { name ->
                val saved =
                    openDemoDatabase(context, clock, name).useDatabase { demoDb ->
                        val demoRepository = RoomChatRepository(demoDb.messages(), clock)
                        val demo = demoRepository.demoChat
                        val seeded = withTimeout(5_000) { demoRepository.observeMessages(demo.conversation.id).first() }
                        assertEquals("Nikita", demo.currentUser.displayName)
                        assertEquals("Sarah", demo.otherUser.displayName)
                        assertEquals(
                            listOf("nikita:Nikita", "sarah:Sarah"),
                            rows(
                                demoDb.openHelper.writableDatabase,
                                "SELECT id || ':' || display_name FROM users ORDER BY id",
                            ),
                        )
                        assertEquals(
                            listOf(demo.conversation.id.value),
                            rows(
                                demoDb.openHelper.writableDatabase,
                                "SELECT id FROM conversations",
                            ),
                        )
                        assertEquals(
                            listOf("nikita", "sarah"),
                            rows(
                                demoDb.openHelper.writableDatabase,
                                "SELECT user_id FROM conversation_participants ORDER BY user_id",
                            ),
                        )
                        assertEquals(
                            listOf(sarah, nikita, nikita, sarah, nikita, sarah, sarah, sarah),
                            seeded.map { it.senderId },
                        )
                        assertEquals(
                            listOf(-9000L, -8940L, -8932L, -300L, -240L, -180L, -173L, -166L)
                                .map(storedNow::plusSeconds),
                            seeded.map { it.sentAt },
                        )
                        assertTrue(seeded.all { it.conversationId == demo.conversation.id && it.text.isNotBlank() })
                        seeded + demoRepository.sendMessage(NewMessage(demo.conversation.id, nikita, "Keep this message"))
                    }
                val later = Clock.fixed(now.plusSeconds(86_400), ZoneOffset.UTC)
                openDemoDatabase(context, later, name).useDatabase { demoDb ->
                    val reopened = RoomChatRepository(demoDb.messages(), later)
                    val messages = withTimeout(5_000) { reopened.observeMessages(reopened.demoChat.conversation.id).first() }
                    assertEquals(saved, messages)
                }
            }
        }

    @Test
    fun reopeningAnEmptyMessageTableDoesNotReseed() =
        runBlocking {
            withDatabaseName { name ->
                openDemoDatabase(context, clock, name).useDatabase {
                    val db = it.openHelper.writableDatabase
                    assertEquals(8, scalar(db, "SELECT COUNT(*) FROM messages"))
                    db.execSQL("DELETE FROM messages")
                }
                openDemoDatabase(context, clock, name).useDatabase {
                    val reopened = RoomChatRepository(it.messages(), clock)
                    assertTrue(withTimeout(5_000) { reopened.observeMessages(reopened.demoChat.conversation.id).first() }.isEmpty())
                }
            }
        }

    @Test
    fun failedDatabaseCreationRollsBackTheSeed() {
        withDatabaseName { name ->
            val failingCallback =
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        assertEquals(8, scalar(db, "SELECT COUNT(*) FROM messages"))
                        error("Initialization failed after seed writes")
                    }
                }
            val builder = Room.databaseBuilder(context, ChatDatabase::class.java, name)
            builder.addCallback(DemoChatSeed(clock))
            builder.addCallback(failingCallback)
            builder.build().useDatabase {
                val failure =
                    assertThrows(IllegalStateException::class.java) {
                        it.openHelper.writableDatabase
                    }
                assertEquals("Initialization failed after seed writes", failure.message)
            }
            Room.databaseBuilder(context, ChatDatabase::class.java, name).build().useDatabase {
                val db = it.openHelper.writableDatabase
                assertEquals(0, scalar(db, "SELECT COUNT(*) FROM users"))
                assertEquals(0, scalar(db, "SELECT COUNT(*) FROM conversations"))
                assertEquals(0, scalar(db, "SELECT COUNT(*) FROM conversation_participants"))
                assertEquals(0, scalar(db, "SELECT COUNT(*) FROM messages"))
            }
        }
    }

    private fun prepareConversation() {
        sqlite.execSQL("INSERT INTO users (id, display_name) VALUES ('nikita', 'Nikita'), ('sarah', 'Sarah')")
        for (conversation in listOf(chat, otherChat)) {
            sqlite.execSQL("INSERT INTO conversations (id) VALUES (?)", arrayOf(conversation.value))
            insertParticipant(conversation.value, nikita.value)
            insertParticipant(conversation.value, sarah.value)
        }
    }

    private fun insertParticipant(
        conversation: String,
        user: String,
    ) {
        sqlite.execSQL(
            "INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
            arrayOf(conversation, user),
        )
    }

    private fun scalar(
        db: SupportSQLiteDatabase,
        sql: String,
    ): Int =
        db.query(sql).use {
            check(it.moveToFirst())
            it.getInt(0)
        }

    private fun rows(
        db: SupportSQLiteDatabase,
        sql: String,
    ): List<String> =
        db.query(sql).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private inline fun withDatabaseName(test: (String) -> Unit) {
        val name = "chat-test-${UUID.randomUUID()}.db"
        try {
            test(name)
        } finally {
            context.deleteDatabase(name)
        }
    }

    private inline fun <T> ChatDatabase.useDatabase(block: (ChatDatabase) -> T): T =
        try {
            block(this)
        } finally {
            close()
        }
}
