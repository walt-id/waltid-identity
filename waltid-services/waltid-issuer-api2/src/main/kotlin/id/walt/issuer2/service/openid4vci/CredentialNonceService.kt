package id.walt.issuer2.service.openid4vci

import id.walt.commons.persistence.ConfiguredPersistence
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class CredentialNonceService {
    private val nonces = ConfiguredPersistence(
        discriminator = "issuer2_credential_nonces",
        defaultExpiration = 5.minutes,
        encoding = { value: String -> value },
        decoding = { it },
    )

    fun issueNonce(): String = UUID.randomUUID().toString().also { nonce ->
        nonces[nonce] = nonce
    }

    fun consumeNonce(nonce: String): Boolean = nonces.getAndRemove(nonce) != null
}
