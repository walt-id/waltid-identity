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
            clientId = clientId ?: setup.clientId ?: config.clientId,
            clientMetadata = clientMetadata ?: setup.clientMetadata ?: config.clientMetadata,
            urlPrefix = uriPrefix ?: setup.urlPrefix ?: config.urlPrefix,
            urlHost = setup.urlHost ?: config.urlHost,
            key = setup.key?.key ?: config.key?.let { KeyManager.resolveSerializedKey(it) },
            x5c = setup.x5c ?: config.x5c
        )

        return newSession
    }


}
