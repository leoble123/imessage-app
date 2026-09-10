package com.leo.imessage.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.leo.imessage.EchoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Brings the connection back after a reboot.
 *
 * Otherwise a phone that restarts overnight comes back with messaging
 * silently off until the app is opened by hand - and the messages missed in
 * between never arrive at all, because nothing was connected to receive them.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val account = EchoApp.instance.account
        // Only if there's a registration to resume - a device that was never
        // signed in shouldn't start a service on boot.
        if (account.relayHost == null) return
        ConnectionService.start(context)
        CoroutineScope(Dispatchers.IO).launch { account.resume() }
    }
}
