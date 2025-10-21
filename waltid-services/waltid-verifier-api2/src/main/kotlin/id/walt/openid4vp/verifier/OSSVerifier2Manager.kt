package id.walt.openid4vp.verifier

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyManager
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionSetup

object OSSVerifier2Manager {

    private val config = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()

    suspend fun createVerificationSession(
        setup: VerificationSessionSetup,
    ): Verification2Session {
        val newSession = VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = setup.clientId ?: config.clientId,
            clientMetadata = setup.clientMetadata ?: config.clientMetadata,
            urlPrefix = setup.urlPrefix ?: config.urlPrefix,
            urlHost = setup.urlHost ?: config.urlHost,
            key = setup.key?.key ?: config.key?.let { KeyManager.resolveSerializedKey(it) },
            x5c = setup.x5c ?: config.x5c
        )

        return newSession
    }


}
