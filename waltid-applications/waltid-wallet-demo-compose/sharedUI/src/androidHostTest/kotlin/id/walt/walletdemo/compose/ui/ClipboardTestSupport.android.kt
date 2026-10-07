package id.walt.walletdemo.compose.ui

import androidx.compose.ui.platform.ClipEntry

internal actual fun ClipEntry.plainText(): String? = clipData.getItemAt(0).text?.toString()
