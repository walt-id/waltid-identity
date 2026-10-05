@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, kotlin.js.ExperimentalWasmJsInterop::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.ClipboardItem
import kotlin.js.JsArray
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.js
import kotlinx.coroutines.await

internal actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)

internal actual suspend fun ClipEntry.readPlainText(): String? =
    readFirstPlainText(clipboardItems).await<JsString>().toString().takeIf { it.isNotEmpty() }

private fun readFirstPlainText(items: JsArray<ClipboardItem>): Promise<JsString> = js("""(async () => {
    const item = Array.from(items).find(item => item.types.includes('text/plain'));
    return item ? await (await item.getType('text/plain')).text() : '';
})()""")
