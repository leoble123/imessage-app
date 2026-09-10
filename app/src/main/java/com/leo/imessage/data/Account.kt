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
)

/** Where the setup flow currently is. */
sealed interface AccountState {
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

    private val store: MessageStore by lazy {
        MessageStore(File(appContext.filesDir, "history.json"))
    }

    private val _state = MutableStateFlow<AccountState>(
        if (relayHost.isNullOrBlank()) AccountState.NeedsRelay else AccountState.NeedsSignIn
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
        val host = relayHost ?: return@withContext false
        val code = relayCode ?: return@withContext false
        try {
            core.configureRelay(host, code, null)
            if (!core.isRegistered()) {
                _state.value = AccountState.NeedsSignIn
                return@withContext false
            }
            becomeReady()
            true
        } catch (e: CoreException) {
            Log.e(TAG, "couldn't reconnect", e)
            _state.value = AccountState.Failed(
                e.friendlyMessage(),
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
        } catch (e: CoreException) {
            _state.value = AccountState.Failed(e.friendlyMessage(), AccountState.NeedsRelay)
        }
    }

    /** Step two: the Apple ID. */
    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        try {
            advance(core.login(email.trim(), password), AccountState.NeedsSignIn)
        } catch (e: CoreException) {
            _state.value = AccountState.Failed(e.friendlyMessage(), AccountState.NeedsSignIn)
        }
    }

    /** Step three, when there is one: the six-digit code. */
    suspend fun submitCode(code: String) = withContext(Dispatchers.IO) {
        val previous = _state.value
        try {
            advance(core.submitDeviceCode(code.trim()), previous)
        } catch (e: CoreException) {
            _state.value = AccountState.Failed(e.friendlyMessage(), previous)
        }
    }

    suspend fun requestSmsCode(phoneId: UInt) = withContext(Dispatchers.IO) {
        val previous = _state.value
        try {
            advance(core.requestSmsCode(phoneId), previous)
        } catch (e: CoreException) {
            _state.value = AccountState.Failed(e.friendlyMessage(), previous)
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
        )
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
        val backend = RustBackend(core, store)
        backend.start()
        _state.value = AccountState.Ready(backend)
    }

    /** Lets the setup screen retry from wherever it failed. */
    fun dismissFailure() {
        (_state.value as? AccountState.Failed)?.let { _state.value = it.previous }
    }

    private companion object {
        const val TAG = "AccountManager"
        const val KEY_HOST = "relay_host"
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
