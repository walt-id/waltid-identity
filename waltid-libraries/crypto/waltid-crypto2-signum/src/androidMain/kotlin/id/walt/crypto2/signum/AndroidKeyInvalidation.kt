package id.walt.crypto2.signum

import android.security.keystore.KeyExpiredException
import android.security.keystore.KeyNotYetValidException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import at.asitplus.signum.indispensable.getJCASignatureInstance
import at.asitplus.signum.indispensable.toJcaPublicKey
import at.asitplus.signum.supreme.os.AndroidKeystoreSigner
import java.security.PrivateKey

/**
 * Keystore can return a handle for a permanently invalidated key. Starting an operation detects
 * this without authenticating or signing. Signum 0.15 does not expose a non-interactive check.
 * Reinitializing the same Android Keystore SPI for verification aborts the pending operation;
 * pinning its provider prevents JCA from switching to a different SPI during that reset.
 */
internal fun AndroidKeystoreSigner.checkKeyInvalidation() {
    if (keyInfo.purposes and KeyProperties.PURPOSE_SIGN == 0) return
    val key = androidKeyStore().getKey(alias, null) as? PrivateKey
        ?: throw SignumKeyNotFoundException(alias)
    val signature = signatureAlgorithm.getJCASignatureInstance("AndroidKeyStoreBCWorkaround").getOrThrow()
    try {
        signature.initSign(key)
    } catch (_: UserNotAuthenticatedException) {
        return // A key needing authorization is not invalidated.
    } catch (_: KeyNotYetValidException) {
        return // Usage dates do not determine whether a key can be reopened.
    } catch (_: KeyExpiredException) {
        return
    }
    // Android aborts failed initialization itself. A successful begin must be explicitly aborted.
    signature.initVerify(publicKey.toJcaPublicKey().getOrThrow())
}
