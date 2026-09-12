package com.leo.imessage.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import uniffi.imessage_core.CoreException
import uniffi.imessage_core.ImessageCore
import uniffi.imessage_core.LoginStep
import java.io.File

/** What Settings shows about the signed-in account. */
data class AccountSummary(
    val primaryHandle: String?,
    val otherHandles: List<String>,
    val relay: String?,
    /**
     * What happened on the last send.
     *
     * A message that silently never arrives is the hardest thing to diagnose
     * from a phone with no tools attached - the failure could be the lookup,
     * the connection, or Apple accepting it and doing nothing with it.
     */
    val lastSend: String? = null,
    /** True when the registration covers every service this build needs. */
    val servicesComplete: Boolean = true,
    /**
     * Set when saved messages couldn't be read.
     *
     * Every conversation vanishing with no explanation is the most alarming
     * thing this app can do, so it says so rather than opening empty.
     */
    val historyProblem: String? = null,
)

/** Where the setup flow currently is. */
sealed interface AccountState {
    /**
     * Reconnecting with credentials already on disk.
     *
     * This is the launch state whenever there's something saved. Without it
     * the sign-in screen renders first - synchronously, before the reconnect
     * has had a chance to run - and flashes away a second later, telling you
     * to sign in to an account you're already signed into.
     */
    data object Restoring : AccountState

    /** No relay configured yet - nothing can happen until there is one. */
    data object NeedsRelay : AccountState

    /** Relay reachable, but no Apple ID signed in. */
    data object NeedsSignIn : AccountState

    /** Apple pushed a code to the account's trusted devices. */
    data object NeedsDeviceCode : AccountState

    /** The account uses SMS for its second factor. */
    data class NeedsSmsCode(val numbers: List<Pair<UInt, String>>) : AccountState

    /** Apple wants something done in a browser first. */
    data class NeedsWebStep(val url: String) : AccountState

    /** Registering with IDS. Can take a few seconds. */
    data object Registering : AccountState

    /**
     * Connected and usable.
     *
     * Typed as the interface rather than [RustBackend] so the demo backend
     * can occupy the same state - the app above this point cannot tell the
     * difference, which is the whole point of the interface.
     */
    data class Ready(val backend: MessagingBackend, val isDemo: Boolean = false) : AccountState

    /** Something failed; [message] is safe to show. [previous] is where to go back to. */
    data class Failed(val message: String, val previous: AccountState) : AccountState
}

/**
 * Owns the account lifecycle - relay, sign-in, registration - and hands back a
 * working [RustBackend] at the end of it.
 *
 * The relay address and pairing code are kept in plain preferences on purpose:
 * they aren't credentials to the Apple ID, they're the address of a service
 * that vends validation data, and the app has to be able to reconnect with
 * them unattended after a restart. The Apple ID password is never stored here
 * at all - it goes straight into the Rust core, which keeps only Apple's own
 * tokens.
 */
class AccountManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("account", Context.MODE_PRIVATE)

    private val core: ImessageCore by lazy {
        ImessageCore(File(appContext.filesDir, "imessage").absolutePath)
    }

    /**
     * Opening the store migrates the old history file and touches the disk, so
     * it is deliberately lazy - and the delegate is held rather than only the
     * value, so [summary] can ask whether it has been opened instead of
     * opening it from whatever thread happens to draw Settings.
     */
    private val storeDelegate = lazy {
        MessageStore(appContext, File(appContext.filesDir, "history.json"))
    }
    private val store: MessageStore by storeDelegate

    /** The address book. Public so the New Message screen can list it. */
    val contacts: Contacts by lazy { Contacts(appContext) }

    /** The live backend, when there is one. */
    val backend: RustBackend?
        get() = (_state.value as? AccountState.Ready)?.backend as? RustBackend

    private val _state = MutableStateFlow<AccountState>(
        // Anything saved means resume() is about to run, so start quiet rather
        // than showing a sign-in form that is about to disappear.
        if (relayHost.isNullOrBlank()) AccountState.NeedsRelay else AccountState.Restoring
    )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    var relayHost: String?
        get() = prefs.getString(KEY_HOST, null)
        private set(value) = prefs.edit().putString(KEY_HOST, value).apply()

    var relayCode: String?
        get() = prefs.getString(KEY_CODE, null)
        private set(value) = prefs.edit().putString(KEY_CODE, value).apply()

    /**
     * Reconnects using saved details, if there are any.
     *
     * Returns true when the app can go straight to the conversation list. This
     * is the launch path, so it deliberately does no work that would need the
     * user - a saved registration means sign-in is already done.
     */
    suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        // Both are written together, so one without the other means the
        // saved setup is incomplete. Returning early without saying so would
        // leave the app sitting on the silent Restoring screen forever.
        val host = relayHost
        val code = relayCode
        if (host.isNullOrBlank() || code.isNullOrBlank()) {
            _state.value = AccountState.NeedsRelay
            return@withContext false
        }
        try {
            core.configureRelay(host, code, null)
            if (!core.isRegistered()) {
                _state.value = AccountState.NeedsSignIn
                return@withContext false
            }
            // A registration made before FaceTime was added covers iMessage
            // only, and Apple silently never routes calls to it. Repaired here
            // rather than forcing a full sign-in, since the saved credentials
            // are enough to re-register on their own.
            // Deliberately at most once a day, even if it doesn't take.
            //
            // Apple rate-limits registrations, and its own error text warns
            // that re-registering to "fix" a problem gets the account
            // temporarily blocked from iMessage. Retrying on every launch is
            // exactly that pattern, and it would turn a passing rate limit
            // into a lasting one.
            val lastAttempt = prefs.getLong(KEY_LAST_REREGISTER, 0L)
            val longEnoughAgo = System.currentTimeMillis() - lastAttempt > REREGISTER_INTERVAL_MS
            if (core.needsServiceRefresh() && longEnoughAgo) {
                prefs.edit().putLong(KEY_LAST_REREGISTER, System.currentTimeMillis()).apply()
                _state.value = AccountState.Registering
                runCatching { core.completeRegistration() }
                    .onFailure { Log.w(TAG, "couldn't refresh registration", it) }
            }
            becomeReady()
            true
        } catch (e: Throwable) {
            // Throwable, not CoreException. Anything else - an Error, a
            // binding-layer failure, a panic surfacing from Rust - used to
            // leave the state at Restoring, which draws a bare spinner with
            // no way out. On the launch path that is an app you cannot even
            // get into Settings to sign out of.
            Log.e(TAG, "couldn't reconnect", e)
            _state.value = AccountState.Failed(
                (e as? CoreException)?.friendlyMessage()
                    ?: "Couldn't reconnect: ${e::class.java.simpleName}: ${e.message}",
                if (relayHost == null) AccountState.NeedsRelay else AccountState.NeedsSignIn,
            )
            false
        }
    }

    /** Step one: point at the relay. */
    suspend fun configureRelay(host: String, code: String) = withContext(Dispatchers.IO) {
        val normalized = host.trim().let {
            // A bare host is the common thing to type; without a scheme the
            // request fails with a URL parse error that explains nothing.
            if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
        }.trimEnd('/')

        try {
            core.configureRelay(normalized, code.trim(), null)
            relayHost = normalized
            relayCode = code.trim()
            if (core.isRegistered()) becomeReady()
            else _state.value = AccountState.NeedsSignIn
        } catch (e: Throwable) {
            _state.value = AccountState.Failed(e.readable(), AccountState.NeedsRelay)
        }
    }

    /** Step two: the Apple ID. */
    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        try {
            advance(core.login(email.trim(), password), AccountState.NeedsSignIn)
        } catch (e: Throwable) {
            _state.value = AccountState.Failed(e.readable(), AccountState.NeedsSignIn)
        }
    }

    /** Step three, when there is one: the six-digit code. */
    suspend fun submitCode(code: String) = withContext(Dispatchers.IO) {
        val previous = _state.value
        try {
            advance(core.submitDeviceCode(code.trim()), previous)
        } catch (e: Throwable) {
            _state.value = AccountState.Failed(e.readable(), previous)
        }
    }

    suspend fun requestSmsCode(phoneId: UInt) = withContext(Dispatchers.IO) {
        val previous = _state.value
        try {
            advance(core.requestSmsCode(phoneId), previous)
        } catch (e: Throwable) {
            _state.value = AccountState.Failed(e.readable(), previous)
        }
    }

    /**
     * Runs the app against sample data instead of a real account.
     *
     * Worth having as more than a developer convenience: without it, a relay
     * that won't come up or an Apple ID that won't take leaves the app with
     * no reachable screens at all, and no way to tell a broken relay from a
     * broken app.
     */
    fun useDemo() {
        _state.value = AccountState.Ready(MockBackend(), isDemo = true)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { core.signOut() }
        prefs.edit().clear().apply()
        _state.value = AccountState.NeedsRelay
    }

    /** What Settings shows - null while running on sample data. */
    fun summary(): AccountSummary? {
        val ready = _state.value as? AccountState.Ready ?: return null
        val backend = ready.backend as? RustBackend ?: return null
        val handles = backend.handles().map(Handles::display)
        return AccountSummary(
            primaryHandle = handles.firstOrNull(),
            otherHandles = handles.drop(1),
            relay = relayHost,
            lastSend = backend.lastSendReport,
            historyProblem = if (storeDelegate.isInitialized()) store.loadFailure else null,
            servicesComplete = runCatching { !core.needsServiceRefresh() }.getOrDefault(true),
        )
    }

    /**
     * Pulls old conversations out of an OpenBubbles export.
     *
     * Worth having because iMessage never sends history: everything before
     * this device registered exists only in whatever client had it before.
     */
    suspend fun importFromOpenBubbles(uri: android.net.Uri): OpenBubblesImport.Result =
        withContext(Dispatchers.IO) {
            val result = OpenBubblesImport.import(
                context = appContext,
                uri = uri,
                store = store,
                contacts = contacts,
                myHandles = backend?.handles().orEmpty(),
            )
            // The backend is holding its own snapshot of the store, so it has
            // to be told rather than discovering the new rows on next launch.
            backend?.reloadFromStore()
            result
        }

    /**
     * Asks Apple whether one address is reachable over iMessage, and reports
     * exactly what came back.
     *
     * This exists because the error a failed send produces cannot answer the
     * question. rustpush raises one error for two different situations - the
     * recipient has no iMessage, or your account is being throttled - because
     * by the time it is raised they look identical: the lookup succeeded and
     * named nobody. What it does *not* cover is the third possibility, which
     * is that the address this app sent was wrong, and that one is only
     * distinguishable by looking at the address it sent.
     *
     * So the report gives the handle as it went out, the account it went out
     * under, and whichever of the three answers Apple actually gave:
     *
     *  - a name back, so the lookup works and the person is reachable;
     *  - an empty answer, which is a throttle or genuinely no iMessage;
     *  - an error with a status code, which is Apple refusing outright.
     */
    suspend fun checkHandle(raw: String): String = withContext(Dispatchers.IO) {
        val backend = backend ?: return@withContext "Not signed in."
        val normalized = runCatching { Handles.normalize(raw) }.getOrNull()
            ?: return@withContext "Couldn't make sense of \"$raw\"."
        val sender = backend.handles().firstOrNull()
            ?: return@withContext "This account has no iMessage address."

        val report = StringBuilder()
        report.appendLine("Asked about: $normalized")
        report.appendLine("Asked as: $sender")
        try {
            val found = core.validateTargets(listOf(normalized), sender)
            if (found.isEmpty()) {
                report.append(
                    "Apple answered, and named nobody. That is either a rate " +
                        "limit or that address genuinely has no iMessage."
                )
            } else {
                report.append("Reachable on iMessage: ${found.joinToString(", ")}")
            }
        } catch (e: Throwable) {
            // The status number in here is the useful part - it is Apple's
            // own word for what it objected to.
            report.append("Apple refused the lookup: ${e.message ?: e::class.java.simpleName}")
        }
        report.toString()
    }

    /**
     * Puts a handful of conversations that aren't real into the database.
     *
     * Alongside the real ones, not instead of them - this is not demo mode,
     * which swaps the backend out and stops anything arriving. The point is
     * to have something to look at while the account is rate limited, and
     * what you look at has to be the real app or it proves nothing.
     */
    suspend fun addSampleConversations(): String = withContext(Dispatchers.IO) {
        val (chats, messages) = SampleData.build()
        // Removed first, so running it twice refreshes the timestamps rather
        // than leaving a set from yesterday sitting under a set from today.
        store.deleteChatsWithPrefix(SampleData.PREFIX)
        store.importInto(chats, messages)
        backend?.reloadFromStore()
        "Added ${chats.size} sample conversations."
    }

    suspend fun removeSampleConversations(): String = withContext(Dispatchers.IO) {
        val removed = store.deleteChatsWithPrefix(SampleData.PREFIX)
        backend?.reloadFromStore()
        if (removed == 0) "There were none to remove." else "Removed $removed."
    }

    /** Called when the app goes to the background, so nothing is left unsaved. */
    suspend fun flush() = runCatching { store.flush() }

    private suspend fun advance(step: LoginStep, previous: AccountState) {
        when (step) {
            is LoginStep.Complete -> {
                _state.value = AccountState.Registering
                try {
                    core.completeRegistration()
                    becomeReady()
                } catch (e: CoreException) {
                    _state.value = AccountState.Failed(e.friendlyMessage(), previous)
                }
            }
            is LoginStep.NeedsDeviceCode -> _state.value = AccountState.NeedsDeviceCode
            is LoginStep.NeedsSmsCode -> _state.value = AccountState.NeedsSmsCode(
                step.phoneNumbers.map { it.id to it.number }
            )
            is LoginStep.NeedsWebStep -> _state.value = AccountState.NeedsWebStep(step.url)
        }
    }

    private suspend fun becomeReady() {
        // Contacts are read before the backend starts so the first render
        // already has names - loading them afterwards makes every thread title
        // visibly change from a number to a name a moment after it appears.
        contacts.load()
        val backend = RustBackend(core, store, contacts, CallAudio(appContext, core), appContext)
        backend.start()
        _state.value = AccountState.Ready(backend)
    }

    /**
     * Re-reads the address book and re-titles existing threads.
     *
     * Called after the contacts permission is granted, which happens *after*
     * the backend is already running and chats are already on screen.
     */
    suspend fun reloadContacts() = withContext(Dispatchers.IO) {
        contacts.load()
        backend?.refreshContactNames()
    }

    /** Lets the setup screen retry from wherever it failed. */
    fun dismissFailure() {
        (_state.value as? AccountState.Failed)?.let { _state.value = it.previous }
    }

    private companion object {
        const val TAG = "AccountManager"
        const val KEY_HOST = "relay_host"
        const val KEY_LAST_REREGISTER = "last_reregister"
        /** Apple's rate limits are measured in hours, so this is generous. */
        const val REREGISTER_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val KEY_CODE = "relay_code"
    }
}

/**
 * Turns a core error into something worth putting on screen.
 *
 * The underlying messages come from rustpush and are written for a developer
 * reading a log - "Failed to parse plist", HTTP status numbers. The common
 * failures get replaced with what the user can actually do about them;
 * anything unrecognised is passed through rather than swallowed, because a
 * vague "something went wrong" is worse than an odd but specific one.
 */
/**
 * Any failure, phrased for a person.
 *
 * Not every failure is a CoreException - the binding layer and the Rust side
 * can both produce something else - and one that isn't must still say
 * something, or the screen it lands on has nothing to show.
 */
private fun Throwable.readable(): String =
    (this as? CoreException)?.friendlyMessage()
        ?: "${this::class.java.simpleName}: ${message ?: "something went wrong"}"

private fun CoreException.friendlyMessage(): String {
    // UniFFI builds the exception message out of the error variant's fields,
    // so it arrives as "reason=..." rather than as the sentence itself.
    val raw = message?.removePrefix("reason=")?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return "Something went wrong."
    return when {
        // Apple's own numbered failures. These are checked first and matched
        // narrowly - an earlier version tested for the word "password"
        // anywhere in the text, which swallowed unrelated errors that merely
        // mentioned it.
        raw.contains("-20101") -> "Apple didn't accept that Apple ID or password."
        raw.contains("-21669") -> "Too many attempts. Wait a few minutes and try again."
        raw.contains("-22406") -> "Apple wants you to finish signing in at appleid.apple.com first."
        // The relay messages are already written for a person by the Rust
        // side, which is the only place that can tell the failures apart, so
        // they pass through untouched rather than being flattened here.
        else -> raw
    }
}
