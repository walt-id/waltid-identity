@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyManager
import id.walt.verifier2.data.*
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreator
import kotlinx.serialization.ExperimentalSerializationApi

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
                is DcApiAnnexCFlowSetup -> setup.origin
            },
            key = setup.core.key?.key ?: config.key?.let { KeyManager.resolveSerializedKey(it) },
            x5c = setup.core.x5c ?: config.x5c,
        )

        return newSession
    }


}
