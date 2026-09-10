package com.leo.imessage.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The phone's address book, keyed by iMessage handle.
 *
 * Without this every conversation is titled with a raw phone number, which is
 * unreadable and makes the app feel like a debugging tool. iMessage sends
 * handles and nothing else - no names, no photos - so the only place a name can
 * come from is the local address book.
 *
 * Everything degrades quietly: no permission, or a handle nobody has saved,
 * just means the number shows instead of a name.
 */
class Contacts(private val context: Context) {

    /** handle (normalized) -> saved contact */
    @Volatile
    private var byHandle: Map<String, SavedContact> = emptyMap()

    @Volatile
    var loaded: Boolean = false
        private set

    data class SavedContact(
        val name: String,
        val handle: String,
        /** A `content://` URI for the contact's photo, when they have one. */
        val photoUri: String?,
        /** "Mobile", "Home", "Work" - shown when someone has several numbers. */
        val label: String?,
    )

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Every saved contact that has a number or address, for the picker. */
    fun all(): List<SavedContact> = byHandle.values
        .distinctBy { it.name + it.handle }
        .sortedBy { it.name.lowercase() }

    fun nameFor(handle: String): String? = byHandle[Handles.normalize(handle)]?.name

    fun photoFor(handle: String): String? = byHandle[Handles.normalize(handle)]?.photoUri

    /**
     * Reads the address book into memory.
     *
     * Done once and held rather than queried per row: a conversation list
     * resolves a name for every visible chat, and a ContentResolver query per
     * row during scrolling is exactly the kind of thing that makes a list
     * stutter.
     */
    fun load() {
        if (!hasPermission()) {
            loaded = true
            return
        }
        val map = HashMap<String, SavedContact>()
        runCatching {
            readPhones(map)
            readEmails(map)
        }.onFailure { Log.e(TAG, "couldn't read contacts", it) }
        byHandle = map
        loaded = true
        Log.i(TAG, "loaded ${map.size} contact handles")
    }

    private fun readPhones(into: MutableMap<String, SavedContact>) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val photo = cursor.getString(2)
                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, cursor.getInt(3), cursor.getString(4) ?: "",
                ).toString()
                // Normalizing on the way in is what makes the lookup work:
                // the book stores "(555) 123-4567" and iMessage sends
                // "tel:+15551234567", and those only meet in E.164.
                val handle = Handles.normalize(number)
                into[handle] = SavedContact(name, handle, photo, label)
            }
        }
    }

    private fun readEmails(into: MutableMap<String, SavedContact>) {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Email.PHOTO_URI,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val photo = cursor.getString(2)
                val handle = Handles.normalize(address)
                // Phone numbers win when both exist: iMessage prefers the
                // number, and it's the one people recognise.
                if (!into.containsKey(handle)) {
                    into[handle] = SavedContact(name, handle, photo, "Email")
                }
            }
        }
    }

    private companion object {
        const val TAG = "Contacts"
    }
}
