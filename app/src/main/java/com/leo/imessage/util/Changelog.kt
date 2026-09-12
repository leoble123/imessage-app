package com.leo.imessage.util

/** How a line in a release reads: something gained, something repaired, or something sharpened. */
enum class ChangeKind(val label: String) {
    NEW("New"),
    FIXED("Fixed"),
    BETTER("Better"),
}

data class Change(val kind: ChangeKind, val text: String)

data class Release(
    val versionCode: Int,
    val versionName: String,
    /** Written out rather than a timestamp - this is read, not sorted. */
    val date: String,
    /** One line on what this build was for. */
    val headline: String,
    val changes: List<Change>,
)

/**
 * What every build of this app changed, newest first.
 *
 * Written by hand rather than generated from commits on purpose. A commit
 * message is addressed to whoever maintains the code; this is addressed to
 * whoever uses it, and the two are rarely the same sentence. It also has to
 * stay readable offline and inside a release build, which rules out reaching
 * for the repository at runtime.
 *
 * Add the new release to the top of this list in the same commit that bumps
 * `versionCode` - the What's New card is driven off the first entry, so a
 * build that forgets shows the previous version's notes under a new number.
 */
object Changelog {

    val releases: List<Release> = listOf(
        Release(
            versionCode = 46,
            versionName = "0.46.0",
            date = "12 September 2026",
            headline = "Something to look at while the account is stuck.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Settings → Testing → Add Sample Conversations. Five threads that " +
                        "aren't real, sitting in the list beside the real ones: a busy " +
                        "one, a group with a lot waiting, a send that failed, one with a " +
                        "reaction, a reply, an edit and a retraction in it, and a quiet " +
                        "one. Remove them and your own messages are untouched.",
                ),
                Change(
                    ChangeKind.NEW,
                    "They answer back. Send one a message and a reply lands a second " +
                        "later, which is the only way to watch an arrival, an unread " +
                        "badge or a notification actually happen.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "Nothing about them touches the network. Their addresses belong to " +
                        "nobody, and asking Apple about addresses that don't exist is a " +
                        "good way to deepen the rate limit they're here to work around.",
                ),
            ),
        ),
        Release(
            versionCode = 45,
            versionName = "0.45.0",
            date = "12 September 2026",
            headline = "Small things that move.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "The What's New card no longer interrupts you on launch. It said on " +
                        "its own schedule what this screen says on request, and the one " +
                        "moment it fired was the moment you had just installed a build in " +
                        "order to look at something else.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Messages flinch when they are said emphatically. \"WHAT?!\" rattles, " +
                        "\"STOP!\" lands. Read off the punctuation, because that is how " +
                        "people actually mark emphasis - send effects need remembering, " +
                        "and nobody remembers them twice.",
                ),
                Change(
                    ChangeKind.NEW,
                    "A pinned circle fills with colour to the level of what is waiting in " +
                        "it, with a slow swell across the surface. One message and eleven " +
                        "used to look identical.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Long-pressing a conversation now shows the last three messages in " +
                        "it, so the menu answers the question you pressed to ask instead " +
                        "of making you open the thread anyway.",
                ),
            ),
        ),
        Release(
            versionCode = 44,
            versionName = "0.44.0",
            date = "11 September 2026",
            headline = "Find out whether it really is a rate limit.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Settings → About → Check iMessage Availability. Type a number or " +
                        "email and it asks Apple directly, then tells you the address it " +
                        "sent, the account it sent it under, and which of three answers " +
                        "came back: a name, an empty answer, or a refusal with a status " +
                        "code. A failed send cannot tell those apart; this can.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Every one-to-one message was being stamped with a freshly invented " +
                        "group identifier. Groups are supposed to carry one and always " +
                        "already had theirs; the only messages reaching that code were " +
                        "the ones that should have carried none, and each got a different " +
                        "random group of its own.",
                ),
            ),
        ),
        Release(
            versionCode = 43,
            versionName = "0.43.0",
            date = "11 September 2026",
            headline = "Out with the frosted plastic, in with the real thing.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Every surface had a white rim graded down its edge and a sheen over " +
                        "its top third. That is what \"foggy plastic\" means - real iOS " +
                        "material has neither. It is one flat opacity over a blur, and the " +
                        "only line on it is the hairline where it meets content.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "Two fixed tones, taken from UIKit's own materials rather than " +
                        "invented, which is also the whole answer to being consistent " +
                        "across backgrounds: a constant cannot disagree with itself the " +
                        "way a derivation can.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "The conversation list is a list again - flat rows on a flat " +
                        "background with a hairline inset to where the text starts, the " +
                        "way Messages has always drawn one.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "The floating controls are control fills now, not material. A " +
                        "translucent pill at the top of a list has nothing behind it to be " +
                        "translucent about, so it came out the same colour as the screen; " +
                        "iOS gives controls an opaque grey for exactly that reason.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The ambient colour field and the glass refraction are gone, along " +
                        "with their settings.",
                ),
            ),
        ),
        Release(
            versionCode = 42,
            versionName = "0.42.0",
            date = "11 September 2026",
            headline = "Glass that actually bends light, and one answer to what it sits on.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Real refraction, computed per pixel on the GPU. A pane is thicker at " +
                        "its edges, so the background is undisturbed across the middle and " +
                        "bent harder and harder toward the rim - with a faint colour " +
                        "fringe, because glass doesn't bend red and blue by the same " +
                        "amount, and a bright line where the curve faces the light.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Glass Refraction is a switch in Settings, under Appearance. It costs " +
                        "a render pass per pane, and a phone that's already warm has " +
                        "better things to spend a GPU on.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Light mode was washed out and dark mode wasn't, because every " +
                        "translucent surface carried its own idea of what was behind it. " +
                        "Measured off your screenshot, the cards and pills were coming out " +
                        "two to seven levels away from the background they sat on - the " +
                        "only thing defining them was their shadow. Nothing decides for " +
                        "itself now: panes, rims, sheens, shadows and scrims are all " +
                        "derived from the one colour actually being painted.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The colour field did nothing in light mode. It was painting " +
                        "near-white blobs onto white - the same code that looked rich in " +
                        "dark mode, pointed in the wrong direction.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "Conversation rows look through to the field for real now instead of " +
                        "being a tinted fill over it, which is also what gives the " +
                        "refraction something to bend.",
                ),
            ),
        ),
        Release(
            versionCode = 41,
            versionName = "0.41.0",
            date = "11 September 2026",
            headline = "Nothing on the home screen is a bar any more.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "The top and bottom bars are gone. Settings, Edit, the filter, search " +
                        "and compose are floating glass pills with the background running " +
                        "past them on every side - which is the only shape that reads as " +
                        "glass. A bar pinned edge to edge over a dark screen is a black " +
                        "rectangle whatever it claims to be made of.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Every conversation is its own pane of glass, inset from the edges " +
                        "with the colour field showing between them.",
                ),
                Change(
                    ChangeKind.NEW,
                    "\"Messages\" is part of the list now, so it scrolls away under the " +
                        "floating controls the way a large title should - and your pinned " +
                        "circles land in a pill of their own once it has gone.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Ambient Background is a switch, in Settings under Appearance.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "The colour field has structure instead of being one even wash - two " +
                        "wide blobs setting the temperature and three tighter ones that " +
                        "read as light with a source.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "With only a couple of conversations the heading vanished on launch. " +
                        "It was measuring a scroll, on a list too short to scroll.",
                ),
            ),
        ),
        Release(
            versionCode = 40,
            versionName = "0.40.0",
            date = "11 September 2026",
            headline = "The conversation list stopped being a grey ledger.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "A slow colour field behind the list, drawn from your accent colour. " +
                        "Every bar on this screen is a real backdrop blur and until now " +
                        "there was nothing behind them worth blurring.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Every conversation has its own colour - the same one its avatar is " +
                        "painted with. It's on the ring, the unread badge and the count, " +
                        "so you can find a thread by colour before you've read a word.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Pinned circles that carry state: the ring breathes while they're " +
                        "typing and holds a coloured arc while something is unread. " +
                        "Nothing moves unless something is actually happening.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Scroll, and the big \"Messages\" heading turns into your pinned " +
                        "circles. There's no reason for a screen full of messages to " +
                        "spend its widest row printing the word \"Messages\".",
                ),
                Change(
                    ChangeKind.NEW,
                    "Rows show the actual photo instead of the word \"Photo\", a reaction " +
                        "as the reaction, and how far your own last message got.",
                ),
                Change(
                    ChangeKind.NEW,
                    "A line under the heading saying what's actually going on - who's " +
                        "typing, what's unread, or that you're all caught up.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Pull the list down past the top for everything waiting across every " +
                        "conversation, and a way to mark it all read that doesn't mean " +
                        "opening each thread in turn.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Swipe rails were drawn behind every row at all times. Invisible " +
                        "while rows were opaque, four coloured stripes the moment they " +
                        "weren't.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The heading sat still through the first inch of every scroll and " +
                        "then moved all at once - it was measuring how much of the first " +
                        "row had gone, which is nothing until the bar's own height has " +
                        "scrolled past.",
                ),
            ),
        ),
        Release(
            versionCode = 39,
            versionName = "0.39.0",
            date = "10 September 2026",
            headline = "The app can now tell you what it changed.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "What's New: after an update, the first launch says what changed. " +
                        "Once, then it gets out of the way.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Release Notes in Settings, under About - every version this app has " +
                        "ever been, so you can see where it's going.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Settings claimed the backend was \"Mock\" and Export Diagnostics " +
                        "reported version 0.1.0. Both had been wrong for eighteen builds.",
                ),
            ),
        ),
        Release(
            versionCode = 38,
            versionName = "0.38.0",
            date = "10 September 2026",
            headline = "Your messages moved into a real database.",
            changes = listOf(
                Change(
                    ChangeKind.BETTER,
                    "Every message used to live in one list in memory, rewritten to disk " +
                        "in full a few times a minute. That's fine at three hundred " +
                        "messages and fatal at fifty thousand. They're in SQLite now, read " +
                        "a page at a time.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "A message arriving in one conversation no longer makes every other " +
                        "one re-read itself.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Apple resends a message when it doesn't hear the acknowledgement. " +
                        "The second copy was raising the unread badge again and " +
                        "re-downloading the attachments.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Opening an already-read conversation rewrote every message in it.",
                ),
                Change(
                    ChangeKind.NEW,
                    "The first 26 tests. They cover the part where a bug can't be undone: " +
                        "iMessage never resends history, so this is the only copy there is.",
                ),
            ),
        ),
        Release(
            versionCode = 37,
            versionName = "0.37.0",
            date = "10 September 2026",
            headline = "A full audit, and everything it found.",
            changes = listOf(
                Change(ChangeKind.FIXED, "A hang on launch when the connection didn't come up."),
                Change(
                    ChangeKind.FIXED,
                    "The microphone stayed live after some calls ended.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Importing an OpenBubbles export said \"not readable\" for large files. " +
                        "It had actually run out of memory - the file is now read as a " +
                        "stream instead of three times over.",
                ),
                Change(ChangeKind.FIXED, "Several failures that happened silently now say so."),
                Change(
                    ChangeKind.BETTER,
                    "Received attachments are cleaned up when nothing points at them any " +
                        "more, instead of growing forever.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "Big attachments wait to be tapped unless you're on Wi-Fi.",
                ),
            ),
        ),
        Release(
            versionCode = 36,
            versionName = "0.36.0",
            date = "10 September 2026",
            headline = "Echo is now Relay.",
            changes = listOf(
                Change(ChangeKind.NEW, "New name and a new icon."),
            ),
        ),
        Release(
            versionCode = 35,
            versionName = "0.35.0",
            date = "10 September 2026",
            headline = "Stop provoking Apple's rate limiter.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "The app re-registered with Apple on every single launch, which is " +
                        "exactly the pattern that turns a temporary rate limit into a " +
                        "lasting one. Now at most once a day.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Phone numbers were shown with the country code cut off.",
                ),
            ),
        ),
        Release(
            versionCode = 34,
            versionName = "0.34.0",
            date = "10 September 2026",
            headline = "A failed send now says why.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "The reason sits under the bubble. \"Not Delivered\" makes every " +
                        "failure look the same, when the difference between \"they aren't " +
                        "on iMessage\" and \"the connection dropped\" is the whole diagnosis.",
                ),
                Change(ChangeKind.NEW, "Tap a failed message to send it again."),
            ),
        ),
        Release(
            versionCode = 33,
            versionName = "0.33.0",
            date = "10 September 2026",
            headline = "Starting a conversation stopped crashing.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Messaging anyone new took the app down. A brand-new conversation has " +
                        "no messages in it, and sorting it beside conversations that do " +
                        "compared two different kinds of number.",
                ),
                Change(ChangeKind.NEW, "A FaceTime button on the conversation list."),
            ),
        ),
        Release(
            versionCode = 32,
            versionName = "0.32.0",
            date = "10 September 2026",
            headline = "Crashes can now be read off the phone.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "A crash writes itself down and shows you the whole thing on the next " +
                        "launch, with a Copy button. Guessing at a crash from a " +
                        "description is how two builds went to the wrong fix.",
                ),
            ),
        ),
        Release(
            versionCode = 31,
            versionName = "0.31.0",
            date = "10 September 2026",
            headline = "Attachments actually leave the phone.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Photos and files were sent as plain text - the recipient got your " +
                        "caption and nothing else, with no sign anything had gone wrong.",
                ),
                Change(ChangeKind.FIXED, "Six smaller bugs found alongside it."),
            ),
        ),
        Release(
            versionCode = 30,
            versionName = "0.30.0",
            date = "10 September 2026",
            headline = "Messages to yourself land somewhere real.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "A message addressed only to your own account had that address " +
                        "filtered out as \"us\", leaving a conversation with nobody in it " +
                        "that could neither send nor receive.",
                ),
            ),
        ),
        Release(
            versionCode = 29,
            versionName = "0.29.0",
            date = "10 September 2026",
            headline = "FaceTime carries sound.",
            changes = listOf(
                Change(ChangeKind.NEW, "Microphone in, earpiece out, encoded as AAC."),
            ),
        ),
        Release(
            versionCode = 28,
            versionName = "0.28.0",
            date = "10 September 2026",
            headline = "FaceTime.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Registration, ringing, answering and hanging up. Incoming calls ring " +
                        "over the lock screen.",
                ),
            ),
        ),
        Release(
            versionCode = 27,
            versionName = "0.27.0",
            date = "10 September 2026",
            headline = "Bring your old conversations with you.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Import an OpenBubbles export from Settings. Worth having because " +
                        "iMessage sends a new device nothing that came before it - " +
                        "whatever client had your history is the only place it exists.",
                ),
            ),
        ),
        Release(
            versionCode = 26,
            versionName = "0.26.0",
            date = "10 September 2026",
            headline = "You can message people again.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Every number and address except your own came back \"not on " +
                        "iMessage\". Two separate causes: numbers were being sent without " +
                        "a country code, and a lookup that should have been a hint was " +
                        "being treated as a gate.",
                ),
                Change(
                    ChangeKind.NEW,
                    "The address book, so conversations are titled with names.",
                ),
            ),
        ),
        Release(
            versionCode = 25,
            versionName = "0.25.0",
            date = "10 September 2026",
            headline = "The two-factor code actually arrives.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "The app waited on a six-digit code it had never asked Apple to send.",
                ),
                Change(ChangeKind.NEW, "Get the code by SMS when no device has it."),
            ),
        ),
        Release(
            versionCode = 24,
            versionName = "0.24.0",
            date = "10 September 2026",
            headline = "Say what actually went wrong.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Every server problem reported itself as \"couldn't reach the relay\", " +
                        "including the ones where the relay answered fine and said no.",
                ),
            ),
        ),
        Release(
            versionCode = 23,
            versionName = "0.23.0",
            date = "10 September 2026",
            headline = "Groundwork.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "The build pieces for running Apple's validation code on the phone " +
                        "itself one day, instead of asking a server to do it.",
                ),
            ),
        ),
        Release(
            versionCode = 22,
            versionName = "0.22.0",
            date = "10 September 2026",
            headline = "Real iMessage, end to end.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Signing in with an Apple ID, registering, sending and receiving - all " +
                        "of it against Apple's own servers.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Messages keep arriving while the app is closed.",
                ),
                Change(ChangeKind.BETTER, "A smaller download."),
            ),
        ),
        Release(
            versionCode = 21,
            versionName = "0.21.0",
            date = "10 September 2026",
            headline = "The app, before it could send anything.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Everything you see: the conversation list, the transcript, bubbles " +
                        "with real tails, tapbacks, replies and threads, editing and " +
                        "unsending, effects, the attachment tray, voice messages, polls, " +
                        "Catch Up, quick replies, and per-chat backgrounds.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Real backdrop blur rather than transparency pretending to be glass, " +
                        "and springs tuned to the numbers iOS actually uses.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "A version number that tells the truth. It had said 0.1.0 for twenty " +
                        "builds.",
                ),
            ),
        ),
    )

    /** The build being run, if it's described here. */
    fun current(versionCode: Int): Release? = releases.firstOrNull { it.versionCode == versionCode }
}
