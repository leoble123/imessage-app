package com.leo.imessage.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
class Calls(private val core: ImessageCore, private val contacts: Contacts?) {

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

            is CallEvent.Connected -> update(event.callId) {
                if (it.stage == Stage.ACTIVE) it
                else it.copy(stage = Stage.ACTIVE, connectedAt = System.currentTimeMillis())
            }

            // In a one-to-one call the other side leaving is the end of it.
            is CallEvent.Left -> update(event.callId) { call ->
                if (call.members.size <= 1) call.copy(stage = Stage.ENDED, endedReason = "Ended")
                else call
            }

            is CallEvent.Declined -> update(event.callId) {
                it.copy(stage = Stage.ENDED, endedReason = "Declined")
            }

            is CallEvent.AnsweredElsewhere -> update(event.callId) {
                it.copy(stage = Stage.ENDED, endedReason = "Answered on another device")
            }

            is CallEvent.Disconnected -> update(event.callId) {
                it.copy(stage = Stage.ENDED, endedReason = "Disconnected")
            }

            is CallEvent.LinkChanged -> {}
        }
    }

    suspend fun place(handles: List<String>, video: Boolean) {
        val targets = handles.map(Handles::normalize)
        val id = core.placeCall(targets, video)
        _current.value = Call(
            id = id,
            stage = Stage.OUTGOING,
            members = peopleOf(targets),
            isVideo = video,
        )
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
        runCatching { core.declineCall(call.id) }
            .onFailure { Log.w(TAG, "decline failed", it) }
        _current.value = call.copy(stage = Stage.ENDED, endedReason = "Declined")
    }

    suspend fun hangUp() {
        val call = _current.value ?: return
        runCatching { core.endCall(call.id) }
            .onFailure { Log.w(TAG, "hang up failed", it) }
        _current.value = call.copy(stage = Stage.ENDED, endedReason = "Ended")
    }

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
