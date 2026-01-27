package id.walt.openid4vp.verifier

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyManager
import id.walt.openid4vp.verifier.data.*
import id.walt.openid4vp.verifier.handlers.sessioncreation.VerificationSessionCreator

object OSSVerifier2Manager {

    private val config = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()

    suspend fun createVerificationSession(
        setup: VerificationSessionSetup,
    ): Verification2Session {
        val newSession = VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = setup.core.clientId ?: config.clientId,
            clientMetadata = setup.core.clientMetadata ?: config.clientMetadata,
            urlPrefix = if (setup is UrlBearingDeviceFlowSetup) setup.urlConfig.urlPrefix ?: config.urlPrefix else null,
            urlHost = when (setup) {
                is UrlBearingDeviceFlowSetup -> setup.urlConfig.urlHost ?: config.urlHost
                is DcApiFlowSetup -> setup.expectedOrigins.firstOrNull() ?: throw IllegalArgumentException("Missing expected origins (at '$.expectedOrigins')")
                is DcApiAnnexCFlowSetup -> throw IllegalArgumentException("Annex C flows must be created via Annex C session handling")
            },
            key = setup.core.key?.key ?: config.key?.let { KeyManager.resolveSerializedKey(it) },
            x5c = setup.core.x5c ?: config.x5c,
        )

        return newSession
    }


}
