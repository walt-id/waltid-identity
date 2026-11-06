package id.walt.walletdemo.compose.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import id.walt.walletdemo.compose.logic.WalletDateTime
import id.walt.walletdemo.compose.logic.WalletDateTimeText
import kotlin.time.Instant

@Composable
internal actual fun rememberWalletTimeFormatter(): (Instant) -> WalletDateTimeText {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0].toLanguageTag()
    var revision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { revision++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        // All actions are protected system broadcasts; no export flag or app permission is needed.
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return remember(locale, revision) {
        val use24HourClock = DateFormat.is24HourFormat(context)
        return@remember { instant: Instant -> WalletDateTime.format(instant, localeTag = locale, use24HourClock = use24HourClock) }
    }
}
