package id.walt.x509.iso

/**
 * Platform-specific bridge to synchronously execute suspend blocks.
 */
internal expect fun <T> blockingBridge(block: suspend () -> T): T
