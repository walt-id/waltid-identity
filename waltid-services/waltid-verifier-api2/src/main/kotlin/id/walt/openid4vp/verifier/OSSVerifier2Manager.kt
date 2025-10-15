package id.walt.openid4vp.verifier

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyManager
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionSetup
import id.walt.verifier.openid.models.authorization.ClientMetadata

object OSSVerifier2Manager {

    private val config = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()

    suspend fun createVerificationSession(
        setup: VerificationSessionSetup,

        clientId: String? = null,
        clientMetadata: ClientMetadata? = null,
        uriPrefix: String? = null,
    ): Verification2Session {
        val newSession = VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = clientId ?: config.clientId,
            clientMetadata = clientMetadata ?: config.clientMetadata,
            uriPrefix = uriPrefix ?: config.urlPrefix,
            uriHost = config.urlHost,
            key = config.key?.let { KeyManager.resolveSerializedKey(it) },
            x5c = config.x5c?.let { listOf(it) }
        )

        return newSession
    }


}
