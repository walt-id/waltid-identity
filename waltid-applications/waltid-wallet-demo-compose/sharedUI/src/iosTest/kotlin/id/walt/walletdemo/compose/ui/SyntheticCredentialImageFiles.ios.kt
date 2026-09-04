package id.walt.walletdemo.compose.ui

import id.walt.walletdemo.compose.ui.resources.Res
import kotlinx.coroutines.runBlocking

internal actual object SyntheticCredentialImageFiles {
    actual fun read(name: String): ByteArray =
        runBlocking { Res.readBytes("files/$name") }
}
