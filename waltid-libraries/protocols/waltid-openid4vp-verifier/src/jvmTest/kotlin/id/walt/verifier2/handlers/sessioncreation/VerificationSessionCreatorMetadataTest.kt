@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.cose.Cose
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class VerificationSessionCreatorMetadataTest {

    @Test
    fun `default mdoc metadata advertises EdDSA support`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery(
                                id = "mdl",
                                format = CredentialFormat.MSO_MDOC,
                                meta = NoMeta,
                            )
                        )
                    )
                )
            ),
            clientId = "verifier",
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
        )

        val mdocMetadata = assertNotNull(
            session.authorizationRequest.clientMetadata?.vpFormatsSupported?.get("mso_mdoc")
        )
        val deviceAuthAlgorithms = mdocMetadata.getValue("deviceauth_alg_values")
            .jsonArray
            .map { it.jsonPrimitive.content.toInt() }

        assertContains(deviceAuthAlgorithms, Cose.Algorithm.EdDSA)
    }
}
