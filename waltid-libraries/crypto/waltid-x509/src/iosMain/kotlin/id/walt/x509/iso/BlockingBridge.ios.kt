package id.walt.x509.iso

import kotlinx.coroutines.runBlocking

internal actual fun <T> blockingBridge(block: suspend () -> T): T = runBlocking { block() }
