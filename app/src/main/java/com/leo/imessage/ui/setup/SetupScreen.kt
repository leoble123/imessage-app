package com.leo.imessage.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import com.leo.imessage.data.AccountManager
import com.leo.imessage.data.AccountState
import com.leo.imessage.data.QrSetupCode
import com.leo.imessage.data.QrSetupPayload
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.leo.imessage.ui.components.pressScale
import com.leo.imessage.ui.components.scaleFrom
import com.leo.imessage.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/** Which way into iMessage the first step is currently offering. */
private enum class SetupWay { RELAY, MAC, PASTE }

/**
 * The setup flow: relay, Apple ID, second factor.
 *
 * Deliberately one card at a time. Each step can fail for its own reasons, and
 * a single long form would make a rejected pairing code look like a rejected
 * password - which sends you re-typing the wrong thing.
 */
@Composable
fun SetupScreen(
    state: AccountState,
    account: AccountManager,
    modifier: Modifier = Modifier,
    /** Fired once sign-in succeeds, so the connection service can start. */
    onConnected: () -> Unit = {},
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    // Kept across steps so going back doesn't wipe what was already typed.
    var host by remember { mutableStateOf(account.relayHost.orEmpty()) }
    var code by remember { mutableStateOf(account.relayCode.orEmpty()) }
    var token by remember { mutableStateOf(account.relayToken.orEmpty()) }

    // Which of the ways in this screen is currently showing. The choice is
    // "how does this phone reach iMessage", which is one decision rather than
    // three separate flows.
    var way by remember {
        mutableStateOf(if (account.usingMacServer) SetupWay.MAC else SetupWay.RELAY)
    }
    var macUrl by remember { mutableStateOf(account.macServer.orEmpty()) }
    var macPassword by remember { mutableStateOf(account.macPassword.orEmpty()) }
    var macResult by remember { mutableStateOf<String?>(null) }
    // Whatever was pasted into the code box: a server QR's contents, a relay's
    // details, or a hardware export.
    var pasted by remember { mutableStateOf("") }
    // Numbers Apple says it can text, once asked. Empty until then - there is
    // nothing to show before the question has been put to it.
    var smsNumbers by remember { mutableStateOf<List<Pair<UInt, String>>>(emptyList()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("") }

    // QR setup: a dedicated full-screen scanner covers this one rather than
    // living in the nav stack, since it's a single yes/no decision (scanned
    // or cancelled) rather than a place you navigate to.
    var showScanner by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }

    // Ready means there is now a live connection to keep alive. Demo mode
    // doesn't get a service - there's nothing connected to hold open.
    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is AccountState.Ready && !state.isDemo) onConnected()
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    fun applyScannedPayload(payload: QrSetupPayload) {
        scanError = null
        when (payload) {
            is QrSetupPayload.MacServer -> {
                way = SetupWay.MAC
                macUrl = payload.url
                macPassword = payload.password
                run { macResult = account.connectToMacServer(payload.url, payload.password) }
            }
            is QrSetupPayload.Relay -> {
                way = SetupWay.RELAY
                host = payload.host
                code = payload.code
                payload.token?.let { token = it }
                run { account.configureRelay(payload.host, payload.code, payload.token ?: token) }
            }
            // Refused by the core, with the reason - see
            // AccountManager.configureHardware. Sent there rather than turned
            // away here so the answer comes from the code that actually knows,
            // and names the Mac the export describes.
            is QrSetupPayload.MacHardware -> run { account.configureHardware(payload.base64) }
        }
    }

    // Never logged, in either of these: what arrives is a server password, a
    // pairing code or a machine's identity, whichever shape it came in.
    fun handleScanResult(text: String?, bytes: ByteArray?) {
        showScanner = false
        val payload = QrSetupCode.parseScan(text, bytes)
        if (payload == null) {
            scanError = "That QR code isn't a setup code this app recognizes. " +
                "Try again, or paste the code instead."
        } else {
            applyScannedPayload(payload)
        }
    }

    fun handlePastedCode(raw: String) {
        val payload = QrSetupCode.parse(raw)
        if (payload == null) {
            scanError = "That isn't a setup code this app recognizes. It takes a " +
                "server QR's contents, a relay as host|code, or a Mac hardware " +
                "export beginning with OABS."
        } else {
            applyScannedPayload(payload)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(
                // A single soft wash rather than the flat background: this is
                // the only screen with nothing behind the glass, and a flat
                // panel on a flat ground has no material to read as.
                Brush.verticalGradient(
                    listOf(
                        palette.accent.copy(alpha = if (palette.isDark) 0.22f else 0.14f),
                        palette.background,
                        palette.background,
                    )
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(48.dp))

            if (state == AccountState.NeedsRelay) {
                ScanQrButton(busy = busy, onClick = { scanError = null; showScanner = true })
                scanError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = palette.secondaryLabel,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(14.dp))
                // The way out when a code won't scan - a dense hardware export
                // on a dim screen is the usual reason - without having to find
                // it inside whichever form happens to be showing.
                Link("Paste the code instead", palette.accent) {
                    scanError = null
                    way = SetupWay.PASTE
                }
                Spacer(Modifier.height(20.dp))
                Text("or set up manually", color = palette.tertiaryLabel, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }

            AnimatedContent(
                targetState = state::class,
                transitionSpec = {
                    // Forward motion between steps, so the flow reads as
                    // progress rather than as a screen being replaced.
                    (slideInHorizontally { it / 6 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally { -it / 6 } + fadeOut(tween(160)))
                },
                label = "setup-step",
            ) { _ ->
                when (state) {
                    AccountState.NeedsRelay -> when (way) {
                        SetupWay.MAC -> Step(
                            title = "Connect to your Mac",
                            detail = "Enter the address and password from BlueBubbles Server " +
                                "on your Mac. The Mac does the talking to Apple, so there's " +
                                "no sign-in, no registration, and nothing for Apple to refuse.",
                            action = "Connect",
                            busy = busy,
                            enabled = macUrl.isNotBlank() && macPassword.isNotBlank(),
                            onAction = {
                                run { macResult = account.connectToMacServer(macUrl, macPassword) }
                            },
                        ) {
                            Field(
                                value = macUrl,
                                onValueChange = { macUrl = it },
                                placeholder = "https://your-server.trycloudflare.com",
                                keyboard = KeyboardType.Uri,
                            )
                            Spacer(Modifier.height(10.dp))
                            Field(
                                value = macPassword,
                                onValueChange = { macPassword = it },
                                placeholder = "Server password",
                            )
                            macResult?.let {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    it,
                                    color = palette.secondaryLabel,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Link("Use a registration server instead", palette.secondaryLabel) {
                                way = SetupWay.RELAY
                                macResult = null
                            }
                            Spacer(Modifier.height(12.dp))
                            Link("Paste a code instead", palette.secondaryLabel) {
                                way = SetupWay.PASTE
                            }
                        }

                        SetupWay.RELAY -> Step(
                            title = "Connect your server",
                            detail = "Enter the address of your registration server and the " +
                                "pairing code it printed. This is what lets the app " +
                                "register with Apple.",
                            action = "Continue",
                            busy = busy,
                            enabled = host.isNotBlank() && code.isNotBlank(),
                            onAction = { run { account.configureRelay(host, code, token) } },
                        ) {
                            Field(
                                value = host,
                                onValueChange = { host = it },
                                placeholder = "150.136.167.146:5005",
                                keyboard = KeyboardType.Uri,
                            )
                            Spacer(Modifier.height(10.dp))
                            Field(
                                value = code,
                                onValueChange = { code = it },
                                placeholder = "Pairing code",
                            )
                            Spacer(Modifier.height(10.dp))
                            // Only a public relay asks for one - Beeper's does,
                            // a self-hosted one takes the code alone - so it is
                            // marked optional rather than left out, which is
                            // what made a public relay unusable from this form.
                            Field(
                                value = token,
                                onValueChange = { token = it },
                                placeholder = "Access token (optional)",
                            )
                            Spacer(Modifier.height(16.dp))
                            Link("I have a Mac running BlueBubbles", palette.accent) {
                                way = SetupWay.MAC
                            }
                            Spacer(Modifier.height(12.dp))
                            Link("Paste a code instead", palette.secondaryLabel) {
                                way = SetupWay.PASTE
                            }
                            Spacer(Modifier.height(12.dp))
                            Link("Look around with sample data", palette.secondaryLabel) {
                                account.useDemo()
                            }
                        }

                        SetupWay.PASTE -> Step(
                            title = "Paste a setup code",
                            detail = "Whatever your QR code contains, pasted as text: a " +
                                "server's address and password, a relay as host|code, or a " +
                                "Mac hardware export beginning with OABS.",
                            action = "Continue",
                            busy = busy,
                            enabled = pasted.isNotBlank(),
                            onAction = { handlePastedCode(pasted) },
                        ) {
                            Field(
                                value = pasted,
                                onValueChange = { pasted = it; scanError = null },
                                placeholder = "Paste the code",
                                tall = true,
                            )
                            Spacer(Modifier.height(16.dp))
                            Link("Type the details instead", palette.secondaryLabel) {
                                way = SetupWay.RELAY
                            }
                        }
                    }

                    AccountState.NeedsSignIn -> Step(
                        title = "Sign in to iMessage",
                        detail = "Your Apple ID password is sent to Apple and never " +
                            "stored on the device.",
                        action = "Sign in",
                        busy = busy,
                        enabled = email.isNotBlank() && password.isNotBlank(),
                        onAction = { run { account.signIn(email, password) } },
                    ) {
                        Field(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "Apple ID",
                            keyboard = KeyboardType.Email,
                        )
                        Spacer(Modifier.height(10.dp))
                        Field(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "Password",
                            keyboard = KeyboardType.Password,
                            secret = true,
                        )
                    }

                    AccountState.NeedsDeviceCode -> Step(
                        title = "Two-factor code",
                        detail = "Apple sent a six-digit code to your other devices.",
                        action = "Verify",
                        busy = busy,
                        enabled = factor.length >= 6,
                        onAction = { run { account.submitCode(factor) } },
                    ) {
                        Field(
                            value = factor,
                            onValueChange = { factor = it.filter(Char::isDigit).take(6) },
                            placeholder = "000000",
                            keyboard = KeyboardType.NumberPassword,
                            centered = true,
                        )
                        Spacer(Modifier.height(14.dp))
                        // The way out when there are no other devices.
                        //
                        // Apple offers device codes whenever the account has
                        // any trusted device, and this screen had no exit - so
                        // an account whose devices have all been removed sat
                        // here waiting for a code that had nowhere to land.
                        //
                        // Which number, though, is Apple's to say. A phone id
                        // is an index into its list, so sending to id 1 was a
                        // guess, and a wrong guess is accepted and then simply
                        // never delivered. So ask first, and show what came
                        // back rather than picking on the account's behalf.
                        if (smsNumbers.isEmpty()) {
                            Text(
                                "Text me the code instead",
                                color = palette.accent,
                                fontSize = 15.sp,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    run {
                                        val found = account.trustedNumbers()
                                        // One number is not a choice worth
                                        // making somebody tap through.
                                        if (found.size == 1) account.requestSmsCode(found[0].first)
                                        else smsNumbers = found
                                    }
                                },
                            )
                        } else {
                            Text(
                                "Send the code to",
                                color = palette.secondaryLabel,
                                fontSize = 13.sp,
                            )
                            smsNumbers.forEach { (id, number) ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    number,
                                    color = palette.accent,
                                    fontSize = 15.sp,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { run { account.requestSmsCode(id) } },
                                )
                            }
                        }
                    }

                    is AccountState.NeedsSmsCode -> Step(
                        title = "Verify by text",
                        detail = if (state.numbers.isEmpty()) {
                            "Enter the code Apple sent you."
                        } else {
                            "Apple can text a code to ${state.numbers.first().second}."
                        },
                        action = "Verify",
                        busy = busy,
                        enabled = factor.length >= 6,
                        onAction = { run { account.submitCode(factor) } },
                    ) {
                        Field(
                            value = factor,
                            onValueChange = { factor = it.filter(Char::isDigit).take(6) },
                            placeholder = "000000",
                            keyboard = KeyboardType.NumberPassword,
                            centered = true,
                        )
                        // Always offered, and it asks Apple which number to
                        // use rather than assuming one. This control used to
                        // render only when Apple had listed the account's
                        // numbers on the way through, which it does not do -
                        // so on the one screen where a text failing to arrive
                        // is the likeliest way to get stuck, there was no way
                        // to ask again.
                        val known = state.numbers
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Send me a code",
                            color = palette.accent,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                run {
                                    val to = known.ifEmpty { account.trustedNumbers() }
                                    when (to.size) {
                                        0 -> Unit // The failure is already on screen.
                                        1 -> account.requestSmsCode(to[0].first)
                                        else -> smsNumbers = to
                                    }
                                }
                            },
                        )
                        // More than one number on the account, so it is a
                        // choice rather than a default.
                        smsNumbers.forEach { (id, number) ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                number,
                                color = palette.accent,
                                fontSize = 15.sp,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { run { account.requestSmsCode(id) } },
                            )
                        }
                    }

                    is AccountState.NeedsWebStep -> Step(
                        title = "One more step",
                        detail = if (state.url.isBlank()) {
                            "Apple needs you to finish signing in on appleid.apple.com, " +
                                "then try again here."
                        } else {
                            "Apple needs you to visit ${state.url} first, then try again."
                        },
                        action = "Try again",
                        busy = busy,
                        enabled = true,
                        onAction = { run { account.signIn(email, password) } },
                        content = {},
                    )

                    AccountState.Registering -> Working(
                        "Registering with Apple",
                        "This takes a few seconds and only happens once.",
                    )

                    // Deliberately wordless. This is the launch path with an
                    // account already set up, and it is usually gone within a
                    // second - a heading and a spinner would be a flash of
                    // text, which is worse than a quiet moment.
                    AccountState.Restoring -> Working("", "")

                    is AccountState.Failed -> Step(
                        title = "That didn't work",
                        detail = state.message,
                        action = "Try again",
                        busy = busy,
                        enabled = true,
                        onAction = { account.dismissFailure() },
                        content = {},
                    )

                    is AccountState.Ready -> Working("Connected", "")
                }
            }

            Spacer(Modifier.height(48.dp))
        }

        if (showScanner) {
            BackHandler { showScanner = false }
            QrScannerScreen(
                onResult = { text, bytes -> handleScanResult(text, bytes) },
                onCancel = { showScanner = false },
            )
        }
    }
}

/** One of the quiet text choices under a step's main action. */
@Composable
private fun Link(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 14.sp,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    )
}

/** The prominent action the QR-based flow is meant to be found by first. */
@Composable
private fun ScanQrButton(busy: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = pressScale(pressed && !busy)

    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .scaleFrom(scale)
            .clip(RoundedCornerShape(25.dp))
            .background(palette.accent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !busy,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Scan QR Code", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Step(
    title: String,
    detail: String,
    action: String,
    busy: Boolean,
    enabled: Boolean,
    onAction: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            color = palette.label,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            detail,
            color = palette.secondaryLabel,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(28.dp))
        content()
        Spacer(Modifier.height(20.dp))

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale = pressScale(pressed && enabled && !busy)

        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .scaleFrom(scale)
                .clip(RoundedCornerShape(25.dp))
                .background(
                    if (enabled) palette.accent else palette.accent.copy(alpha = 0.35f)
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled && !busy,
                    onClick = onAction,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(action, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Working(title: String, detail: String) {
    val palette = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, color = palette.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(detail, color = palette.secondaryLabel, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    centered: Boolean = false,
    /**
     * Room for something long. A hardware export runs to hundreds of
     * characters, and a 50dp single-line box shows about six of them at a
     * time - enough to make a correct paste look like a mangled one.
     */
    tall: Boolean = false,
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (tall) 140.dp else 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.fieldBackground)
            .padding(horizontal = 16.dp, vertical = if (tall) 12.dp else 0.dp),
        contentAlignment = if (tall) Alignment.TopStart else Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                color = palette.tertiaryLabel,
                fontSize = if (tall) 13.sp else 17.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !tall,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = palette.label,
                // Small enough that a pasted blob reads as one block rather
                // than as a wall scrolling past a slot.
                fontSize = if (tall) 13.sp else 17.sp,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                // A six-digit code reads as digits, not prose.
                letterSpacing = if (centered) 6.sp else 0.sp,
            ),
            cursorBrush = SolidColor(palette.accent),
            visualTransformation =
                if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboard,
                imeAction = if (tall) ImeAction.Default else ImeAction.Next,
                autoCorrectEnabled = false,
            ),
            modifier = if (tall) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        )
    }
}
