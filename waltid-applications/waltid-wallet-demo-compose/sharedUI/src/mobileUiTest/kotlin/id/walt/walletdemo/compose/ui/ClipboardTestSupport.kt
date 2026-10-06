package id.walt.walletdemo.compose.ui

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

internal class RecordingClipboard : Clipboard {
    var entry: ClipEntry? = null
        private set

    override suspend fun getClipEntry(): ClipEntry? = entry

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        entry = clipEntry
    }
}

internal expect fun ClipEntry.plainText(): String?
