package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

/**
 * Handles the platform's own back gesture on platforms that have one.
 *
 * Declared per platform rather than taken from `androidx.compose.ui.backhandler` so Android registers
 * against the dispatcher its host Activity already owns: a surface the operating system started has to
 * turn a back gesture into an Activity result, which is not something a platform-neutral back API can
 * express. Platforms with no system back gesture supply a no-op, so a screen may call this
 * unconditionally.
 *
 * @param enabled Whether this handler consumes the gesture. Pass false to let it propagate to the host.
 */
@Composable
internal expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
