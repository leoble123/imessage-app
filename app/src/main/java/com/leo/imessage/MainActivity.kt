package com.leo.imessage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leo.imessage.data.MockBackend
import com.leo.imessage.ui.AppRoot
import com.leo.imessage.ui.theme.iMessageTheme

class MainActivity : ComponentActivity() {

    // Swapped for the rustpush-backed implementation once the Rust core is
    // wired in; every screen is written against the MessagingBackend
    // interface, so nothing above this line changes when that happens.
    private val backend by lazy { MockBackend() }
    private val settings = com.leo.imessage.ui.theme.AppSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            iMessageTheme(settings) {
                AppRoot(backend)
            }
        }
    }
}
