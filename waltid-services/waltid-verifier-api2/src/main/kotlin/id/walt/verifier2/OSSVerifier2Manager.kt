@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyManager
import id.walt.verifier2.data.*
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreator
import id.walt.verifier2.utils.computeX509HashAudience
import kotlinx.serialization.ExperimentalSerializationApi

object OSSVerifier2Manager {

    private val config get() = ConfigManager.getConfig<OSSVerifier2ServiceConfig>()

    suspend fun createVerificationSession(
        setup: VerificationSessionSetup,
    ): Verification2Session {
        val effectiveClientId = setup.core.clientId
            ?: setup.core.x5c?.firstOrNull()?.let(::computeX509HashAudience)
            ?: config.clientId

        val newSession = VerificationSessionCreator.createVerificationSession(
            setup = setup,
            clientId = effectiveClientId,
            clientMetadata = setup.core.clientMetadata ?: config.clientMetadata,
            urlPrefix = if (setup is UrlBearingDeviceFlowSetup) setup.urlConfig.urlPrefix ?: config.urlPrefix else null,
            urlHost = when (setup) {
                is UrlBearingDeviceFlowSetup -> setup.urlConfig.urlHost ?: config.urlHost
                is DcApiAnnexDFlowSetup -> setup.expectedOrigins.firstOrNull()
                    ?: throw IllegalArgumentException("Missing expected origins (at '$.expectedOrigins')")

                is DcApiAnnexCFlowSetup -> setup.origin
            },
            key = setup.core.key?.key ?: config.key?.let { KeyManager.resolveSerializedKey(it) },
            x5c = setup.core.x5c ?: config.x5c,
        )

        return newSession
    }


}
