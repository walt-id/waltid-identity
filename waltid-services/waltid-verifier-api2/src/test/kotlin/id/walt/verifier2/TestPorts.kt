package id.walt.verifier2

import java.net.ServerSocket

internal fun freePort(): Int = ServerSocket(0).use { it.localPort }
