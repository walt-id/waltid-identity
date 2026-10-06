package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

/** Standalone camera handoff exists only where the platform exposes a supported public action. */
@Composable
internal expect fun rememberSystemCameraLauncher(): (() -> Unit)?
