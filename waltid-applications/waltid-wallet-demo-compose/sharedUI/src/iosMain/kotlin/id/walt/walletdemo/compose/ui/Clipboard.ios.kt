package id.walt.walletdemo.compose.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)

@OptIn(ExperimentalComposeUiApi::class)
internal actual suspend fun ClipEntry.readPlainText(): String? = getPlainText()
