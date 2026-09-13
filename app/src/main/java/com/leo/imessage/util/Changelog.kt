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
            versionCode = 64,
            versionName = "0.64.0",
            date = "13 September 2026",
            headline = "Get the code by text, and two things that only pretended to work.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "\"Text me the code instead\", on the two-factor screen. Apple offers " +
                        "device codes whenever an account has any trusted device, and that " +
                        "screen had no exit - so an account whose devices have been removed " +
                        "sat waiting for a code with nowhere to land. A trusted phone " +
                        "number survives that.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The resend link on the text screen is visible. It only appeared when " +
                        "Apple had listed your numbers, which it never does on the way " +
                        "through, so the one screen where a text can go missing had no way " +
                        "to ask for another.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Quick Replies insert. The composer read the saved draft once when the " +
                        "conversation opened and ignored it afterwards, so anything setting " +
                        "the draft from outside was dropped on the floor - which is every " +
                        "Quick Reply, silently.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The demo no longer shows the other person typing while you type. It " +
                        "was writing your own outbound typing notice into the flag that " +
                        "means somebody else is composing. Sample replies now show that " +
                        "flag properly - they think for a moment first.",
                ),
            ),
        ),
        Release(
            versionCode = 63,
            versionName = "0.63.0",
            date = "12 September 2026",
            headline = "@everyone, and mentions generally.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Typing @ in a group brings up who is in it, with Everyone at the " +
                        "top. iMessage has no notify-all of its own - a mention names one " +
                        "person - but a mention of you reaches you through a thread you " +
                        "have muted. So Everyone expands into a real mention of each " +
                        "person rather than pretending to be a broadcast, and every phone " +
                        "in the group lights up.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Mentions are real mentions, not text with an @ in front. They go out " +
                        "as the same thing an iPhone sends, so they arrive highlighted and " +
                        "they notify.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "A name that merely appears in a sentence does not ping anybody. Only " +
                        "the names picked from the list travel as mentions, and a token " +
                        "edited afterwards quietly goes back to being text.",
                ),
            ),
        ),
        Release(
            versionCode = 62,
            versionName = "0.62.0",
            date = "12 September 2026",
            headline = "A sign-in no longer dies because a public server had a bad minute.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Signing in gets three attempts at provisioning instead of one. The " +
                        "step runs against a public anisette server over a websocket, and " +
                        "only when the device identity is new - so a single abandoned " +
                        "session landed squarely on a first sign-in and looked like the " +
                        "app was broken.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "That failure said what it was. The server has states the client did " +
                        "not know about, so it died listing the four it recognised, which " +
                        "reads like a bug here rather than a server giving up. Any state " +
                        "it cannot answer now ends the session and retries it.",
                ),
            ),
        ),
        Release(
            versionCode = 61,
            versionName = "0.61.0",
            date = "12 September 2026",
            headline = "Sign in as your actual Mac instead of a server pretending to be one.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "The app can take a Mac's exported hardware identity instead of a " +
                        "registration server. The server manufactures validation data by " +
                        "emulating Apple's own software; a Mac has the hardware that data " +
                        "claims to describe. Nothing visible from here told them apart - " +
                        "registration was accepted either way and the connection came up " +
                        "either way - which is why it took this long to suspect.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Nothing is pinned to a relay any more. The device identity was the " +
                        "one part of the setup that could not be swapped out, which " +
                        "quietly made an emulated one the only option there was.",
                ),
            ),
        ),
        Release(
            versionCode = 60,
            versionName = "0.60.0",
            date = "12 September 2026",
            headline = "The log drowned the evidence in mutexes.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "The protocol log is readable. Every acquisition and release of " +
                        "every lock was being written out - about fifteen lines per " +
                        "keepalive - so an exported log covered five idle minutes and " +
                        "contained nothing that had actually happened. The send it was " +
                        "meant to capture had already scrolled away.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "The push connection is holding. The reconnect loop that was tearing " +
                        "it down several times a second is gone - it now sits on one " +
                        "socket and pings once a minute, which is what a connection that " +
                        "can receive anything looks like.",
                ),
            ),
        ),
        Release(
            versionCode = 59,
            versionName = "0.59.0",
            date = "12 September 2026",
            headline = "Sending asks Apple again, so nothing here needs re-registering.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "A send now refreshes the lookup rather than accepting a cached " +
                        "answer. An empty result was cached like any other and trusted " +
                        "for an hour, so a single failed lookup made that person " +
                        "unreachable for an hour with no request sent at all. A minute-" +
                        "long floor still applies, so this costs a query the client " +
                        "would make anyway.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "Recovering no longer means registering. Clearing that cache was " +
                        "bundled into re-registration, which is the one genuinely rate-" +
                        "limited step and a bad thing to need on every attempt. " +
                        "Re-register is still there for when the registration really is " +
                        "the problem; it is no longer the price of a stale lookup.",
                ),
            ),
        ),
        Release(
            versionCode = 58,
            versionName = "0.58.0",
            date = "12 September 2026",
            headline = "The log showed it was never asking Apple at all.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Lookups were being answered out of a stale cache instead of being " +
                        "sent. Empty results are cached like any other and trusted for an " +
                        "hour, so one failed lookup answered for every lookup of that " +
                        "address for the next hour - the diagnostic included. The log " +
                        "shows it plainly: the cache is opened and closed and no query " +
                        "ever goes out. Re-registering now clears that cache, and the " +
                        "availability check always asks for real.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The push connection was tearing itself down several times a second. " +
                        "Reconnecting while already connected presents the same push " +
                        "certificate twice, Apple allows one, and the two evict each " +
                        "other forever. Nothing can arrive over a socket with that " +
                        "lifespan, which is most likely why nothing did.",
                ),
            ),
        ),
        Release(
            versionCode = 57,
            versionName = "0.57.0",
            date = "12 September 2026",
            headline = "The protocol version was two revisions stale, copied from a commented-out example.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Every request to Apple carried protocol version 1640. The working " +
                        "client on this same account and relay sends 1660, and so does " +
                        "the live config in the protocol library - 1640 came from a " +
                        "commented-out block one line below it. A stale version is not " +
                        "refused: the registration is accepted, the connection comes up, " +
                        "and every lookup is answered with nobody in it. Which is exactly " +
                        "what has been happening.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The device UDID was never set. It is an Option that the library " +
                        "unwraps with an expect, so it was a crash waiting for whichever " +
                        "service asked first, and it is one of the identifiers Apple ties " +
                        "a registration to. It is generated once and kept.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "One of the four services the working client registers was missing. " +
                        "It belongs to FindMy and looks unrelated to messaging, but the " +
                        "set registered is also the set the identity is built with.",
                ),
            ),
        ),
        Release(
            versionCode = 56,
            versionName = "0.56.0",
            date = "12 September 2026",
            headline = "Register with Apple again, which is the one thing nothing here could do.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "Re-register with Apple, in Settings. Registration was skipped " +
                        "whenever a saved one existed - correct almost always, and wrong " +
                        "in the one case that looks exactly like being blocked. Apple " +
                        "keeps one registration per device, so another app signed in to " +
                        "the same account takes it over, and what is left still connects, " +
                        "still gets answered, and resolves nobody. Nothing arrives either. " +
                        "There was no way out of that state and now there is one.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Export Diagnostics does something. The report went out as an intent " +
                        "extra, which has about a megabyte for everything in flight - fine " +
                        "for a version string, not for a protocol log. It was being " +
                        "rejected, the error was swallowed, and the button looked dead. " +
                        "It writes a file now, and says so out loud when it cannot.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "More room for importing an OpenBubbles export. This is a raise, not " +
                        "a fix: the importer still holds the whole export in memory at " +
                        "once, and a big enough one will still run out. Streaming it " +
                        "properly is the actual repair and is not in this build.",
                ),
            ),
        ),
        Release(
            versionCode = 55,
            versionName = "0.55.0",
            date = "12 September 2026",
            headline = "Read Apple's actual answer instead of guessing at it.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "The protocol core logged the full decoded lookup response at debug " +
                        "level and the app ran at info, so every failed send has been " +
                        "receiving Apple's real answer and throwing it away unread. " +
                        "That is why the reason for a failure has had to be inferred " +
                        "from an error string that cannot tell the cases apart.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "Export Diagnostics now carries the protocol log. It used to carry " +
                        "the app version, the theme and the phone model, none of which " +
                        "has ever been why a message did not send. Note that at this " +
                        "level the log contains push tokens and public keys - not " +
                        "passwords or private keys, but identifiers, so share it with " +
                        "someone helping you and nowhere else.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "Group avatars are a disc. The cluster hung off two corners while " +
                        "the ring around it and the unread liquid rising inside it were " +
                        "circles of the full frame, so they could not line up - the ring " +
                        "was true to the frame and the thing inside it was not.",
                ),
            ),
        ),
        Release(
            versionCode = 54,
            versionName = "0.54.0",
            date = "12 September 2026",
            headline = "Check iMessage Availability actually runs now.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "The availability check was cancelling itself before it asked " +
                        "Apple anything. It ran on a scope belonging to the prompt " +
                        "that started it, and the prompt closes the instant you tap " +
                        "Check - so the lookup was killed on the way out, every time, " +
                        "and it had never once reached the network.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "A cancelled check no longer prints its cancellation where the " +
                        "answer goes. \"The coroutine scope left the composition\" was " +
                        "the app describing its own plumbing and passing it off as " +
                        "something Apple said.",
                ),
            ),
        ),
        Release(
            versionCode = 53,
            versionName = "0.53.0",
            date = "12 September 2026",
            headline = "Check iMessage Availability now asks under every address, not just one.",
            changes = listOf(
                Change(
                    ChangeKind.BETTER,
                    "The handle check tries the lookup under each of your registered " +
                        "addresses separately and prints what each one answered. " +
                        "Sends only ever ask under the first, so a check that also " +
                        "asked under one address could only ever agree with the " +
                        "failure and explain nothing.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "It also lists the addresses this account is registered as, and " +
                        "says outright when the one you asked about is your own - " +
                        "sending to yourself skips the check that fails for everyone " +
                        "else, so it proves nothing either way.",
                ),
            ),
        ),
        Release(
            versionCode = 52,
            versionName = "0.52.0",
            date = "12 September 2026",
            headline = "A FaceTime screen worth looking at, and six settings that finally do something.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "The call screen moves. The caller's own two colours drift behind " +
                        "them as three slow blobs, rings leave the avatar while it is " +
                        "ringing, and the controls sit in one capsule of glass instead of " +
                        "loose on a flat gradient.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Speakerphone, during a call. It picks the output device explicitly " +
                        "rather than setting the old flag that newer Android quietly " +
                        "ignores, and the button follows the route that actually took " +
                        "effect rather than the one it asked for.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Six settings were switches wired to nothing: Compact Chat List, " +
                        "Show Typing Indicators, Swipe to Reply, Swipe for Timestamps, " +
                        "Unread Badges, and Quick Replies. Every one of them now changes " +
                        "what the app does.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Quick Replies had somewhere to be written and nowhere to be used - " +
                        "the hook for them was declared and never called. They appear " +
                        "above the attachment menu now, and land in the draft rather " +
                        "than sending on one tap.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "A failed send no longer blames a rate limit it cannot see. The old " +
                        "wording offered patience for what is usually an address with no " +
                        "iMessage on it, and the two reach the app differently enough to " +
                        "tell apart.",
                ),
            ),
        ),
        Release(
            versionCode = 51,
            versionName = "0.51.0",
            date = "12 September 2026",
            headline = "Incoming bubbles lift off the wallpaper instead of sinking into it.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Incoming bubbles were being pushed toward the wallpaper rather than " +
                        "lifted off it - a dark wash over a dark background, which is the " +
                        "background. They are the blurred wallpaper under a thin bright " +
                        "wash now, the way a real pane of frosted glass catches light, and " +
                        "the numbers come off a measurement of the real thing rather than " +
                        "a guess.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "A rim that can be seen, and a contact shadow under every bubble. The " +
                        "edge used to be a hairline faint enough to vanish against anything " +
                        "busy, so the shape had nothing holding it together.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Over a pale wallpaper the wash inverts to dark, so the same bubble " +
                        "stays readable on a bright background without turning into a slab.",
                ),
            ),
        ),
        Release(
            versionCode = 50,
            versionName = "0.50.0",
            date = "12 September 2026",
            headline = "The swipe actions can be pressed, and the light-wallpaper bubbles can be seen.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "Swipe actions are buttons now. The rail slid open and showed Unread, " +
                        "Mute and Delete, and none of the three had a tap handler on it - " +
                        "the only one you could actually reach was whichever fired from a " +
                        "full swipe. They press, dim under the finger, tick, and close " +
                        "the row behind them.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Incoming bubbles were close to invisible on a light wallpaper. They " +
                        "were a white pane, and a white pane over a pale background is the " +
                        "background. They hold a grey of their own now, and the edge that " +
                        "was a barely-there hairline on light material got enough weight " +
                        "to draw the shape.",
                ),
            ),
        ),
        Release(
            versionCode = 49,
            versionName = "0.49.0",
            date = "12 September 2026",
            headline = "Bubbles that take their colour from the wallpaper, not from a painted-on shine.",
            changes = listOf(
                Change(
                    ChangeKind.BETTER,
                    "Glass bubbles are real glass now. The old ones wore four painted " +
                        "highlights - a sheen, a lit rim, a caustic bounce, a diagonal " +
                        "glare - which sat in the same place no matter what was behind " +
                        "them, so they read as moulded plastic. They blur the wallpaper " +
                        "underneath instead, with a single hairline edge.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Incoming bubbles over a dark wallpaper had it backwards: a near-clear " +
                        "white pane where iOS uses charcoal. Outgoing bubbles stay close " +
                        "to solid, because blue thinned over a purple background stops " +
                        "being blue.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The conversation title read through its own pill on a busy wallpaper. " +
                        "The pill is thicker, and the whole header flips light or dark to " +
                        "follow the wallpaper rather than the theme.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "The message placeholder no longer disappears into a bright background.",
                ),
            ),
        ),
        Release(
            versionCode = 48,
            versionName = "0.48.0",
            date = "12 September 2026",
            headline = "Backgrounds that follow the clock and the sky.",
            changes = listOf(
                Change(
                    ChangeKind.NEW,
                    "The conversation list can have a wallpaper again - Settings → " +
                        "Appearance → Home Background - including three that work " +
                        "themselves out: Time of Day, Weather, and both together.",
                ),
                Change(
                    ChangeKind.NEW,
                    "Weather is read from your approximate location, using only the " +
                        "position your phone already knew rather than waking the GPS. " +
                        "Nothing is asked for until you pick one of those backgrounds, " +
                        "and refusing it falls back to the clock, which needs nothing.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "Unnamed numbers had avatars reading \"+5\". Initials were taken from " +
                        "the first letter of each word, and an unnamed conversation is " +
                        "titled with a phone number. A number has no initials, so those " +
                        "now show a person the way iOS does.",
                ),
                Change(
                    ChangeKind.FIXED,
                    "The unread liquid vanished instead of draining when you opened a " +
                        "chat. The animation was written; the whole thing was being " +
                        "removed before it could play.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "Unread level no longer scales in a straight line. It rises fast over " +
                        "the first few messages, where the difference is worth seeing, " +
                        "and flattens toward a ceiling it never reaches - so a hundred " +
                        "unread no longer drowns the face underneath.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "The liquid clings to the glass at the edges instead of running " +
                        "straight into it, and sits almost still until something lands, " +
                        "which is when it briefly comes alive.",
                ),
            ),
        ),
        Release(
            versionCode = 47,
            versionName = "0.47.0",
            date = "12 September 2026",
            headline = "The floating controls are glass again, and the liquid has depth.",
            changes = listOf(
                Change(
                    ChangeKind.FIXED,
                    "The pinned dock and the other floating controls were solid slabs. " +
                        "They had been taken to ninety-four percent opaque to stop them " +
                        "vanishing against a white screen, which worked and cost the " +
                        "whole effect - nothing passed through, so nothing smeared. They " +
                        "are translucent again, with a tint dark enough to still be a " +
                        "shape on their own.",
                ),
                Change(
                    ChangeKind.BETTER,
                    "The unread level looks like liquid instead of a coloured wash. One " +
                        "sine wave at that size is a straight line, so there are two at " +
                        "different speeds; the body is graded from nearly clear at the " +
                        "surface to full colour at depth, because that is what a tinted " +
                        "volume does; and there is a bright meniscus riding the surface, " +
                        "which is the detail that says \"boundary between two materials\" " +
                        "rather than \"shape that got cropped\".",
                ),
                Change(
                    ChangeKind.BETTER,
                    "The ring around a pinned circle now means one thing only - they are " +
                        "typing. It used to also draw an arc for unread, which was right " +
                        "when unread was a dot and is two signals too many now that it " +
                        "fills the circle.",
                ),
            ),
        ),
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
