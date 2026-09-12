package com.leo.imessage.data

import java.util.concurrent.TimeUnit

/**
 * Conversations that aren't real, for testing the app without Apple.
 *
 * Every screen in this app is downstream of having messages in it, and for
 * long stretches there have been none to look at - a brand-new account starts
 * empty, and a rate-limited one stays that way. Judging a transcript, an
 * unread level or a long-press peek against two conversations and a failed
 * send is guesswork.
 *
 * These are deliberately *not* demo mode, which swaps the whole backend out
 * and stops real messages arriving. They are rows in the real database
 * alongside real threads, so what you are testing is the real app.
 *
 * Everything here is keyed under [PREFIX], which is what makes that safe:
 * removal is exact, nothing can be mistaken for a real conversation, and the
 * backend refuses to put anything on the wire for a chat whose id starts with
 * it. That last part matters more than it sounds - these handles belong to
 * nobody, and quietly asking Apple about them is a good way to make a rate
 * limit worse while trying to work around one.
 */
object SampleData {

    /** Everything sample is keyed under this, and removal is exactly this. */
    const val PREFIX = "sample:"

    fun isSample(chatId: String): Boolean = chatId.startsWith(PREFIX)

    /** What a sample conversation replies with, so arrivals can be watched. */
    private val REPLIES = listOf(
        "wait what?!",
        "haha ok",
        "yeah that works for me",
        "one sec",
        "SAME!",
        "ok but hear me out",
    )

    fun replyTo(text: String): String = when {
        text.trim().endsWith("?") -> "good question, let me think"
        text.isBlank() -> "?"
        else -> REPLIES[(text.hashCode().let { if (it < 0) -it else it }) % REPLIES.size]
    }

    /**
     * The whole set, built fresh so the timestamps are always recent enough
     * to be interesting - a fixed date would have every thread reading "last
     * March" a week after it was written.
     */
    fun build(): Pair<List<Chat>, List<Message>> {
        val now = System.currentTimeMillis()
        fun minutes(n: Long) = now - TimeUnit.MINUTES.toMillis(n)
        fun hours(n: Long) = now - TimeUnit.HOURS.toMillis(n)
        fun days(n: Long) = now - TimeUnit.DAYS.toMillis(n)

        val ava = Contact("${PREFIX}ava", "Ava Chen", "tel:+15550100001")
        val marco = Contact("${PREFIX}marco", "Marco Diaz", "tel:+15550100002")
        val priya = Contact("${PREFIX}priya", "Priya Raman", "tel:+15550100003")
        val dad = Contact("${PREFIX}dad", "Dad", "tel:+15550100004")
        val jordan = Contact("${PREFIX}jordan", "Jordan Blake", "mailto:jordan@example.com")
        val kai = Contact("${PREFIX}kai", "Kai Okafor", "tel:+15550100005")

        val chats = mutableListOf<Chat>()
        val messages = mutableListOf<Message>()

        fun chat(
            id: String,
            name: String,
            people: List<Contact>,
            unread: Int = 0,
            pinned: Boolean = false,
        ) {
            chats += Chat(
                id = PREFIX + id,
                displayName = name,
                participants = people,
                lastMessage = null,
                unreadCount = unread,
                isPinned = pinned,
            )
        }

        fun msg(
            chatId: String,
            id: String,
            text: String,
            at: Long,
            from: Contact? = null,
            state: DeliveryState = DeliveryState.DELIVERED,
            tapbacks: List<Tapback> = emptyList(),
            attachments: List<Attachment> = emptyList(),
            replyToId: String? = null,
            unsentText: String? = null,
            editHistory: List<String> = emptyList(),
            failureReason: String? = null,
        ) {
            messages += Message(
                id = "$PREFIX$chatId-$id",
                chatId = PREFIX + chatId,
                text = text,
                timestamp = at,
                isFromMe = from == null,
                senderId = from?.id ?: "me",
                deliveryState = state,
                tapbacks = tapbacks,
                attachments = attachments,
                replyToId = replyToId?.let { "$PREFIX$chatId-$it" },
                unsentText = unsentText,
                editHistory = editHistory,
                editedAt = if (editHistory.isEmpty()) null else at + 40_000,
                failureReason = failureReason,
            )
        }

        // 1. A busy one-to-one, pinned, with a few waiting. The default case.
        chat("ava", "Ava Chen", listOf(ava), unread = 3, pinned = true)
        msg("ava", "1", "did you end up going to that place on 4th", hours(28), ava)
        msg("ava", "2", "yeah last night actually", hours(27))
        msg("ava", "3", "it was genuinely so good", hours(27))
        msg("ava", "4", "ok adding it to the list", hours(27), ava)
        msg("ava", "5", "are you around this weekend?", minutes(52), ava)
        msg("ava", "6", "there's a thing on saturday", minutes(51), ava)
        msg("ava", "7", "starts at 7 if you want to come", minutes(50), ava)

        // 2. A group with a lot waiting - the unread level should be near full,
        //    and the peek should show three different names.
        chat("crew", "Taco Crew", listOf(marco, priya, ava), unread = 11, pinned = true)
        msg("crew", "1", "ok so tuesday?", hours(6), marco)
        msg("crew", "2", "tuesday works", hours(6), priya)
        msg("crew", "3", "i can do tuesday after 6", hours(5))
        msg("crew", "4", "WHAT?! you're finally free??", hours(5), marco)
        msg("crew", "5", "miracles happen", hours(5))
        msg("crew", "6", "booking it", minutes(34), priya)
        msg("crew", "7", "7pm, the usual", minutes(33), priya)
        msg("crew", "8", "see you there", minutes(32), marco)
        msg("crew", "9", "bring cash this time", minutes(31), ava)
        msg("crew", "10", "and don't be late", minutes(30), marco)
        msg("crew", "11", "he's always late", minutes(29), priya)
        msg("crew", "12", "ONE TIME", minutes(28), ava)
        msg("crew", "13", "it was twice", minutes(27), marco)

        // 3. A send that failed, which is the state hardest to get on purpose.
        chat("dad", "Dad", listOf(dad))
        msg("dad", "1", "call me when you get a chance", days(2), dad)
        msg("dad", "2", "will do", days(2))
        msg(
            "dad", "3", "sorry - just seeing this now", hours(3),
            state = DeliveryState.FAILED,
            failureReason = "CoreException: no route to that address",
        )

        // 4. One of everything the transcript can draw: a reaction, a reply,
        //    an edit, a retraction and an attachment with no bytes behind it.
        chat("jordan", "Jordan Blake", listOf(jordan))
        msg("jordan", "1", "sending the deck now", days(1), jordan)
        msg(
            "jordan", "2", "", days(1), jordan,
            attachments = listOf(
                Attachment(
                    id = "${PREFIX}att1",
                    fileName = "Q3-deck.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 2_400_000,
                ),
            ),
        )
        msg(
            "jordan", "3", "this is great, thank you", days(1),
            tapbacks = listOf(
                Tapback(TapbackKind.HEART, fromMe = false, senderId = jordan.id),
            ),
        )
        msg(
            "jordan", "4", "one note on slide 6 though", hours(20),
            replyToId = "2",
            editHistory = listOf("one note on slide 5 though"),
        )
        msg("jordan", "5", "", hours(19), jordan, unsentText = "ignore that, I fixed it")
        msg("jordan", "6", "all good now", hours(18), jordan)

        // 5. Nothing waiting, and a receipt on the last thing I said - the
        //    quiet state, which is most threads most of the time.
        chat("kai", "Kai Okafor", listOf(kai))
        msg("kai", "1", "thanks for covering yesterday", days(4), kai)
        msg("kai", "2", "anytime", days(4), state = DeliveryState.READ)

        // `lastMessage` is derived on read, so nothing here has to work out
        // which message is newest.
        return chats to messages
    }
}
