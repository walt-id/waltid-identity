package id.walt.walletdemo.compose.ui

import androidx.compose.ui.platform.ClipEntry

// Compose exposes plain-text clip entry constructors separately on each platform.
internal expect fun plainTextClipEntry(text: String): ClipEntry
internal expect suspend fun ClipEntry.readPlainText(): String?
