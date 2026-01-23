package id.walt.openid4vp.verifier

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyManager
import id.walt.openid4vp.verifier.data.DcApiFlowSetup
import id.walt.openid4vp.verifier.data.UrlBearingDeviceFlowSetup
import id.walt.openid4vp.verifier.data.Verification2Session
import id.walt.openid4vp.verifier.data.VerificationSessionSetup
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
            },
            key = setup.core.key?.key ?: config.key?.let { KeyManager.resolveSerializedKey(it) },
            x5c = setup.core.x5c ?: config.x5c,
        )

        return newSession
    }


}
