package id.walt.x509.iso

internal actual fun <T> blockingBridge(block: suspend () -> T): T {
    throw UnsupportedOperationException(
        "blockingBridge is not supported on JS; use the suspend APIs instead."
    )
}
