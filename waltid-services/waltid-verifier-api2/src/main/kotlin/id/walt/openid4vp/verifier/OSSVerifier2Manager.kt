package id.walt.openid4vp.verifier

import id.walt.commons.config.ConfigManager
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
            uriPrefix = uriPrefix ?: config.urlPrefix
        )

        return newSession
    }


}
