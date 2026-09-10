package com.leo.imessage.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.leo.imessage.MainActivity
import com.leo.imessage.data.Chat
import com.leo.imessage.data.Message

/**
 * Incoming-message notifications.
 *
 * Uses MessagingStyle rather than a plain text notification, which is what
 * gets Android to render it as a conversation - sender avatars, the message
 * history stacked, inline reply where the launcher supports it - instead of
 * a generic line of text with an app icon. It also lets the system group
 * them per chat, so ten messages from one person collapse into one entry
 * rather than ten.
 */
object Notifier {
    private const val CHANNEL_ID = "messages"
    private val history = mutableMapOf<String, MutableList<Message>>()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "New messages"
                enableVibration(true)
                setShowBadge(true)
            }
        )
    }

    /** Posts (or updates) the notification for one conversation. */
    fun post(context: Context, chat: Chat, message: Message) {
        if (message.isFromMe || message.text.isBlank() && message.attachments.isEmpty()) return
        ensureChannel(context)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val senderName = chat.participants
            .firstOrNull { it.id == message.senderId }
            ?.displayName
            ?: chat.displayName

        val thread = history.getOrPut(chat.id) { mutableListOf() }
        thread += message
        // Keep the tail only: a notification that tries to show a hundred
        // messages is slower to build than it is useful to read.
        if (thread.size > 6) thread.removeAt(0)

        val me = Person.Builder().setName("You").build()
        // Builder-style setters, not Kotlin property syntax: these return the
        // style for chaining, so Kotlin never synthesises them as properties.
        var style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(chat.displayName.takeIf { chat.isGroup })
            .setGroupConversation(chat.isGroup)
        thread.forEach { m ->
            val who = if (m.isFromMe) null else Person.Builder()
                .setName(
                    chat.participants.firstOrNull { it.id == m.senderId }?.displayName
                        ?: senderName
                )
                .build()
            style = style.addMessage(m.text.ifBlank { "Attachment" }, m.timestamp, who)
        }

        val open = PendingIntent.getActivity(
            context,
            chat.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_CHAT_ID, chat.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(style)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setOnlyAlertOnce(false)
            .setSilent(chat.isMuted)
            .build()

        runCatching { manager.notify(chat.id.hashCode(), notification) }
    }

    /** Opening a conversation clears its notification and its backlog. */
    fun clear(context: Context, chatId: String) {
        history.remove(chatId)
        runCatching {
            NotificationManagerCompat.from(context).cancel(chatId.hashCode())
        }
    }

    const val EXTRA_CHAT_ID = "chat_id"
}
