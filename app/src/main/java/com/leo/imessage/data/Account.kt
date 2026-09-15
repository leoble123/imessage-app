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
     * Exported Mac hardware, base64, when signing in as a real machine.
     *
     * Takes precedence over the relay when both are set. A relay manufactures
     * validation data by emulating Apple's own IMDAppleServices; a Mac has the
     * hardware that data describes. Nothing visible from here distinguishes
     * them - registration is accepted either way and the push connection comes
     * up either way - so when a real machine is on offer it is the one used.
     */
    var hardwareBlob: String?
        get() = prefs.getString(KEY_HARDWARE, null)
        private set(value) = prefs.edit().putString(KEY_HARDWARE, value).apply()

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
        // A saved hardware blob is thrown away rather than used.
        //
        // Signing in as a Mac cannot work from the phone - the validation data
        // it would have to produce is computed by Apple's own code, which is
        // what a relay runs - so a blob saved by an earlier version would
        // otherwise take precedence forever and fail every launch, with no way
        // back to the relay short of clearing app data.
        if (!hardwareBlob.isNullOrBlank()) {
            Log.w(TAG, "discarding saved Mac hardware: that route cannot work from here")
            hardwareBlob = null
        }
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

    /**
     * The other step one: sign in as a Mac instead of through a relay.
     *
     * Takes the blob an OpenAbsinthe export produces - the tag OABS, a shared
     * flag, then the machine's model, MAC address, serial, UUIDs, board id,
     * build number, ROM and board serial, each also in the sealed form Apple's
     * validation produces.
     */
    suspend fun configureHardware(base64: String) = withContext(Dispatchers.IO) {
        val cleaned = base64.trim().replace("\n", "").replace(" ", "")
        val bytes = try {
            android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT)
        } catch (e: Throwable) {
            _state.value = AccountState.Failed(
                "That isn't valid base64 - paste the whole hardware string.",
                AccountState.NeedsRelay,
            )
            return@withContext
        }
        try {
            core.configureHardware(bytes)
            hardwareBlob = cleaned
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

    /**
     * The numbers Apple is willing to text a code to.
     *
     * Asked rather than assumed. A phone id is an index into whatever Apple
     * returns for this account, not a stable address, so sending to id 1 was a
     * guess - and when the guess is wrong Apple accepts the request and simply
     * never sends anything, which is indistinguishable from the text being
     * slow. Empty means Apple named none, and the reason is on screen.
     */
    suspend fun trustedNumbers(): List<Pair<UInt, String>> = withContext(Dispatchers.IO) {
        val previous = _state.value
        try {
            core.trustedPhoneNumbers().map { it.id to it.number }
        } catch (e: Throwable) {
            _state.value = AccountState.Failed(e.readable(), previous)
            emptyList()
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
    /**
     * Registers this device with Apple again, from scratch.
     *
     * The normal path skips registration whenever a saved one exists, which
     * is right nearly always - it is the rate-limited step - and wrong in the
     * one case that looks exactly like being blocked. IDS holds one
     * registration per device identity, so another client signing the same
     * Apple ID in with the same device details takes it over, and what is
     * left behind still connects, still gets its lookups answered, and
     * resolves nobody. Nothing arrives either. There is no status code for
     * it, and no way out but registering again.
     *
     * Reports the handles afterwards, because the useful confirmation is not
     * that it returned but what Apple now says this account is.
     */
    suspend fun reRegister(): String = withContext(Dispatchers.IO) {
        val before = backend?.handles().orEmpty()
        try {
            core.forceRegister()
        } catch (e: Throwable) {
            return@withContext "Registration failed: ${e.message ?: e::class.java.simpleName}"
        }
        val after = backend?.handles().orEmpty()
        buildString {
            appendLine("Registered again.")
            if (after.isEmpty()) {
                append("Apple returned no addresses, which means it did not take.")
            } else {
                appendLine("This account is now:")
                after.forEach { appendLine("  $it") }
                if (after != before) {
                    append("That is different from before - try sending again now.")
                } else {
                    append("Same as before. Try sending again; if it still fails the registration was not the problem.")
                }
            }
        }
    }

    /**
     * Moves to a different registration relay without signing out.
     *
     * The relay is what supplies validation data, and validation data is what
     * Apple uses to decide whether this endpoint is a real machine. Everything
     * else in the registration has been checked and matches a client known to
     * work; the relay is the one thing that has never been varied.
     *
     * Deliberately not a sign-out. The Apple ID session and the device
     * identity survive in the saved registration, so nothing here goes near
     * the password or a verification code - which matters, because being sent
     * back through two-factor is its own way to lose a day.
     */
    suspend fun switchRelay(host: String, code: String): String = withContext(Dispatchers.IO) {
        val normalized = host.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
        }.trimEnd('/')
        val trimmedCode = code.trim()
        if (normalized.isBlank() || trimmedCode.isBlank()) {
            return@withContext "Both the server and the code are needed."
        }

        try {
            core.switchRelay(normalized, trimmedCode, null)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (e: Throwable) {
            return@withContext "Couldn't switch: ${e.readable()}"
        }

        relayHost = normalized
        relayCode = trimmedCode

        try {
            becomeReady()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (e: Throwable) {
            return@withContext "Registered against the new server, but starting up " +
                "failed: ${e.readable()}. Reopen the app."
        }

        val handles = backend?.handles().orEmpty()
        buildString {
            appendLine("Now registered through $normalized.")
            if (handles.isEmpty()) {
                append("Apple returned no addresses, so it did not take.")
            } else {
                appendLine("Sending from: ${handles.joinToString()}")
                append("Try a message, then run Check Registration With Apple.")
            }
        }
    }

    /**
     * Asks Apple which devices it currently has registered on this account,
     * and says whether this phone is one of them.
     *
     * This answers the one question a failing send cannot. When IDS hands
     * back `status 0` and an empty identity list for every address asked
     * about - including addresses that certainly have devices on them - the
     * protocol gives no reason, because from Apple's side nothing went
     * wrong. Two very different things look identical from in here:
     *
     *  - this registration has been superseded (another client signed the
     *    same Apple ID in with the same device details and took it over), in
     *    which case re-registering fixes it, or
     *  - the registration is live and Apple is declining to answer it, in
     *    which case re-registering does nothing and will only burn attempts.
     *
     * Apple's own device list separates them, and it is a plain read - no
     * registration, nothing rate limited - so it is safe to run whenever
     * sending looks wrong.
     */
    suspend fun registrationStatus(): String = withContext(Dispatchers.IO) {
        val status = try {
            core.registrationStatus()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (e: Throwable) {
            return@withContext "Couldn't ask Apple: ${e.message ?: e::class.java.simpleName}"
        }

        buildString {
            if (status.weAreRegistered) {
                appendLine("This phone IS registered with Apple.")
                appendLine(
                    "So an empty lookup is not a stale registration - Apple is " +
                        "answering this device and returning nobody. Re-registering " +
                        "will not change that; do not keep doing it.",
                )
            } else {
                appendLine("This phone is NOT in Apple's device list.")
                appendLine(
                    "The registration was taken over - that is exactly why every " +
                        "lookup comes back empty and nothing arrives. Re-register " +
                        "once, then check here again.",
                )
            }
            appendLine()
            appendLine("Apple lists this device at: ${status.ourHandles.joinToString().ifBlank { "(none)" }}")
            if (status.ourHandles.none { it.startsWith("tel:") }) {
                appendLine(
                    "No phone number on this registration, so anyone texting your " +
                        "number reaches your other devices, not this one.",
                )
            }
            // The certificate's copy, which only says what was true when it
            // was issued. Apple adds handles to a live registration - a phone
            // number registered from another client lands on every
            // registration the account has - and never tells the client, so
            // this drifts silently and the app goes on believing the old set.
            if (status.cachedHandles.toSet() != status.ourHandles.toSet()) {
                appendLine(
                    "This app still thinks it is: " +
                        status.cachedHandles.joinToString().ifBlank { "(none)" },
                )
                appendLine("Re-register once to pick up the difference.")
            }
            appendLine("Our push token: ${status.ourPushToken}")
            appendLine()

            // Same device name, different push token, more than once.
            //
            // Apple keeps one registration per device identity, so duplicates
            // are not extra devices - they are earlier attempts that were
            // never replaced, because the identity they registered under was
            // regenerated instead of reused. They sit in Apple's list
            // indefinitely and are worth clearing out by hand at
            // account.apple.com; a lookup has no way to tell which of them is
            // the endpoint that should be answered.
            val ghosts = status.devices
                .groupBy { it.name }
                .filterValues { it.size > 1 }
            if (ghosts.isNotEmpty()) {
                val extra = ghosts.values.sumOf { it.size - 1 }
                appendLine(
                    "$extra duplicate registration(s) - same device, registered more " +
                        "than once. These are leftovers from earlier attempts, not " +
                        "real devices. Remove them at account.apple.com.",
                )
                appendLine()
            }

            appendLine("Apple has ${status.devices.size} registration(s) on this account:")
            status.devices.forEach { d ->
                appendLine("  ${if (d.isThisDevice) "> " else "  "}${d.name}")
                appendLine("      token ${d.pushToken}")
                appendLine("      trusted: ${d.isHsaTrusted}")
                appendLine("      ${d.handles.joinToString().ifBlank { "no handles" }}")
                appendLine("      services: ${d.subServices.joinToString().ifBlank { "none" }}")
            }

            // Whether Apple counts this registration as a trusted device for
            // the account, which is a different question from whether it is
            // registered at all - and one the protocol never volunteers.
            val us = status.devices.firstOrNull { it.isThisDevice }
            if (us != null && !us.isHsaTrusted) {
                appendLine()
                appendLine(
                    "This registration is not marked trusted, while others on the " +
                        "account are. An untrusted registration is exactly one that " +
                        "Apple answers and does not serve identities to.",
                )
            }
        }
    }

    suspend fun checkHandle(raw: String): String = withContext(Dispatchers.IO) {
        val backend = backend ?: return@withContext "Not signed in."
        val normalized = runCatching { Handles.normalize(raw) }.getOrNull()
            ?: return@withContext "Couldn't make sense of \"$raw\"."
        val mine = backend.handles()
        if (mine.isEmpty()) return@withContext "This account has no iMessage address."

        val report = StringBuilder()
        report.appendLine("Asked about: $normalized")
        report.appendLine("Typed as: ${raw.trim()}")
        report.appendLine()
        report.appendLine("This account is registered as:")
        mine.forEach { report.appendLine("  $it") }
        if (mine.any { it.equals(normalized, ignoreCase = true) }) {
            report.appendLine()
            report.appendLine(
                "Note: that address is one of yours. Sending to yourself skips " +
                    "the check that fails for everyone else, so a send to it " +
                    "reporting success proves nothing."
            )
        }
        report.appendLine()

        // Every handle, not just the first one.
        //
        // The send path picks whichever handle comes back first and asks
        // under that one, so if the account holds several and only some can
        // resolve anyone, a single-sender check agrees with the failure and
        // explains nothing. Asking under each of them separates "this
        // account cannot look anyone up" from "this account is asking as the
        // wrong one of its own addresses", and those have completely
        // different fixes.
        var anyReachable = false
        var anyRefused = false
        for (sender in mine) {
            try {
                val found = core.validateTargets(listOf(normalized), sender)
                if (found.isEmpty()) {
                    report.appendLine("as $sender -> answered, named nobody")
                } else {
                    anyReachable = true
                    report.appendLine("as $sender -> reachable: ${found.joinToString(", ")}")
                }
            } catch (e: Throwable) {
                anyRefused = true
                // The status number in here is the useful part - it is
                // Apple's own word for what it objected to.
                report.appendLine(
                    "as $sender -> refused: ${e.message ?: e::class.java.simpleName}"
                )
            }
        }

        report.appendLine()
        report.append(
            when {
                anyReachable ->
                    "Reachable. If sending still fails, this app is asking under " +
                        "the wrong one of your addresses - that is a bug here, not " +
                        "a limit on your account."
                anyRefused ->
                    "Apple refused the lookup outright. That is the one answer that " +
                        "really is a throttle or a block, and the status code above " +
                        "says which."
                else ->
                    "Every address answered and named nobody. Apple is talking to " +
                        "this account, so it is not blocked - either that person " +
                        "genuinely has no iMessage, or this account's registration " +
                        "is not in a state Apple will resolve anyone for."
            }
        )
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
                // Registering and starting up are separate failures, and
                // conflating them is what filled this account with devices.
                //
                // completeRegistration is the step that puts this device on
                // the Apple ID. Once it returns, the account is registered and
                // the state is on disk. Starting up afterwards - reading
                // contacts, opening the message client - can still fail, and
                // that used to land in the same catch and send you back to the
                // sign-in screen. Signing in again registers *another* device
                // to fix a problem that was never about registration, and the
                // only visible symptom is a device list that keeps growing.
                //
                // Throwable rather than CoreException, too: the old catch only
                // covered errors from the core, so anything thrown by the
                // Android side - a contacts permission, an audio device -
                // escaped it entirely and failed the sign-in from outside.
                try {
                    core.completeRegistration()
                } catch (e: Throwable) {
                    _state.value = AccountState.Failed(e.readable(), previous)
                    return
                }
                try {
                    becomeReady()
                } catch (e: Throwable) {
                    Log.e(TAG, "registered, but couldn't start up", e)
                    _state.value = AccountState.Failed(
                        "Signed in, but the app couldn't finish starting: " +
                            "${e.readable()}\n\nYour account is registered - " +
                            "try again rather than signing in a second time.",
                        AccountState.Restoring,
                    )
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
        //
        // Not fatal, though. Names are a courtesy; messaging works without
        // them, and reloadContacts picks them up later once the permission is
        // granted. Letting this throw took the whole sign-in down with it, at
        // the point where the account had already been registered.
        runCatching { contacts.load() }
            .onFailure { Log.w(TAG, "couldn't read contacts; carrying on without names", it) }
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
        const val KEY_HARDWARE = "hardware_blob"
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
