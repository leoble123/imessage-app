package com.leo.imessage.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.imessage_core.CallEvent
import uniffi.imessage_core.ImessageCore

/**
 * The state of the one call this device is on.
 *
 * One at a time on purpose. FaceTime supports several sessions at once, but a
 * phone has one screen, one earpiece and one camera, and every extra
 * simultaneous call is a state machine nobody can see. A second incoming call
 * while one is live is declined rather than silently queued.
 */
class Calls(
    private val core: ImessageCore,
    private val contacts: Contacts?,
    private val audio: CallAudio? = null,
) {

    enum class Stage {
        /** Ringing, we haven't answered. */
        INCOMING,

        /** We're calling out, waiting for them. */
        OUTGOING,

        /** Somebody joined - the call is live. */
        ACTIVE,

        /** Over, briefly, so the UI can show why before it disappears. */
        ENDED,
    }

    data class Call(
        val id: String,
        val stage: Stage,
        val members: List<Contact>,
        val isVideo: Boolean,
        /** When the call actually connected, for the duration timer. */
        val connectedAt: Long? = null,
        /** Why it ended - "Declined", "No answer". Only set at [Stage.ENDED]. */
        val endedReason: String? = null,
    ) {
        val title: String
            get() = members.joinToString(", ") { it.displayName }
                .ifBlank { "FaceTime" }
    }

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private val _current = MutableStateFlow<Call?>(null)
    val current: StateFlow<Call?> = _current.asStateFlow()

    /** Our own handles, so we aren't listed as a participant in our own call. */
    @Volatile
    var myHandles: List<String> = emptyList()

    fun onEvent(event: CallEvent) {
        when (event) {
            is CallEvent.Incoming -> {
                val existing = _current.value
                // Already on one. Answering would drop the live call and
                // there's nowhere to show two, so this one is turned away
                // rather than left ringing forever.
                if (existing != null && existing.stage != Stage.ENDED) {
                    Log.i(TAG, "declining a second call while one is active")
                    return
                }
                _current.value = Call(
                    id = event.callId,
                    stage = Stage.INCOMING,
                    members = peopleOf(event.members),
                    isVideo = event.isVideo,
                )
            }

            is CallEvent.Ringing -> update(event.callId) {
                // Only meaningful for a call we placed.
                if (it.stage == Stage.OUTGOING) it else it
            }

            is CallEvent.Joined -> update(event.callId) {
                if (it.stage == Stage.ACTIVE) it
                else it.copy(stage = Stage.ACTIVE, connectedAt = System.currentTimeMillis())
            }

            is CallEvent.Connected -> {
                // The media session only exists once this arrives, so this is
                // the earliest the microphone can be opened.
                startAudio(event.callId)
                update(event.callId) {
                    if (it.stage == Stage.ACTIVE) it
                    else it.copy(stage = Stage.ACTIVE, connectedAt = System.currentTimeMillis())
                }
            }

            // In a one-to-one call the other side leaving is the end of it.
            is CallEvent.Left -> update(event.callId) { call ->
                if (call.members.size <= 1) {
                    audio?.stop()
                    call.copy(stage = Stage.ENDED, endedReason = "Ended")
                } else {
                    call
                }
            }

            is CallEvent.Declined -> {
                audio?.stop()
                update(event.callId) {
                    it.copy(stage = Stage.ENDED, endedReason = "Declined")
                }
            }

            is CallEvent.AnsweredElsewhere -> {
                // The microphone has to be released here too. Every other way
                // a call ends stopped it; this one didn't, so answering on
                // another device left this phone recording.
                audio?.stop()
                update(event.callId) {
                    it.copy(stage = Stage.ENDED, endedReason = "Answered on another device")
                }
            }

            is CallEvent.Disconnected -> {
                audio?.stop()
                update(event.callId) {
                    it.copy(stage = Stage.ENDED, endedReason = "Disconnected")
                }
            }

            is CallEvent.LinkChanged -> {}
        }
    }

    /**
     * Starts a call, and says so when it can't.
     *
     * A failure used to be swallowed whole - no call screen, no error,
     * nothing at all on screen - which is indistinguishable from the button
     * not being wired up.
     */
    suspend fun place(handles: List<String>, video: Boolean) {
        val targets = handles.map(Handles::normalize)
        val people = peopleOf(targets)
        try {
            val id = core.placeCall(targets, video)
            _current.value = Call(
                id = id,
                stage = Stage.OUTGOING,
                members = people,
                isVideo = video,
            )
        } catch (e: Throwable) {
            Log.e(TAG, "couldn't place call", e)
            _current.value = Call(
                id = "failed",
                stage = Stage.ENDED,
                members = people,
                isVideo = video,
                endedReason = e.message?.removePrefix("reason=")?.trim()
                    ?: "Couldn't start the call",
            )
        }
    }

    suspend fun answer() {
        val call = _current.value ?: return
        runCatching { core.answerCall(call.id) }
            .onSuccess {
                _current.value = call.copy(
                    stage = Stage.ACTIVE,
                    connectedAt = System.currentTimeMillis(),
                )
            }
            .onFailure { fail(call, it) }
    }

    suspend fun decline() {
        val call = _current.value ?: return
        audio?.stop()
        runCatching { core.declineCall(call.id) }
            .onFailure { Log.w(TAG, "decline failed", it) }
        _current.value = call.copy(stage = Stage.ENDED, endedReason = "Declined")
    }

    suspend fun hangUp() {
        val call = _current.value ?: return
        audio?.stop()
        runCatching { core.endCall(call.id) }
            .onFailure { Log.w(TAG, "hang up failed", it) }
        _current.value = call.copy(stage = Stage.ENDED, endedReason = "Ended")
    }

    /** Opens the audio path, once the call has a media session behind it. */
    private fun startAudio(callId: String) {
        val audio = audio ?: return
        scope.launch {
            runCatching { core.startCallAudio(callId) }
                .onSuccess { audio.start(callId) }
                .onFailure { Log.w(TAG, "couldn't open call audio", it) }
        }
    }

    var muted: Boolean
        get() = audio?.muted ?: false
        set(value) { audio?.muted = value }

    /**
     * Loudspeaker or earpiece. Returns the route that actually took effect,
     * which is not always the one asked for.
     */
    fun setSpeaker(on: Boolean): Boolean = audio?.setSpeaker(on) ?: false

    /** True when there is a microphone path at all. */
    val hasAudio: Boolean get() = audio != null

    /** Clears an ended call once its "call ended" moment has been shown. */
    fun dismiss() {
        if (_current.value?.stage == Stage.ENDED) _current.value = null
    }

    suspend fun createLink(): String = core.createCallLink()

    private fun fail(call: Call, error: Throwable) {
        Log.e(TAG, "call failed", error)
        _current.value = call.copy(
            stage = Stage.ENDED,
            endedReason = error.message?.removePrefix("reason=") ?: "Call failed",
        )
    }

    private fun peopleOf(handles: List<String>): List<Contact> = handles
        .map(Handles::normalize)
        .filterNot { it in myHandles }
        .distinct()
        .map { Handles.contact(it, contacts?.nameFor(it)) }

    private inline fun update(callId: String, block: (Call) -> Call) {
        val call = _current.value ?: return
        // Events for a call that isn't the one on screen are stale - a
        // previous call that ended while its teardown was still in flight.
        if (call.id != callId) return
        _current.value = block(call)
    }

    private companion object {
        const val TAG = "Calls"
    }
}
