package id.walt.x509.iso

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0) {
        "Random byte count must not be negative."
    }
    if (size == 0) return ByteArray(0)

    val randomBytes = ByteArray(size)
    val status = randomBytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
    }
    check(status == 0) { "SecRandomCopyBytes failed with status $status." }
    return randomBytes
}
