@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyManager
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.policies2.vp.policies.AudienceCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.DeviceAuthMdocVpPolicy
import id.walt.policies2.vp.policies.TransactionDataHashCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.TransactionDataMdocVpPolicy
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.OpenId4VPConfig
import id.walt.verifier2.data.Verification2Session
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val DEMO_TRANSACTION_DATA_TYPE = "org.waltid.transaction-data.payment-authorization"

class VerificationSessionCreatorTransactionDataPolicyTest {

    @Test
    fun `signed authorization request payload includes issuer matching client id`() = runTest {
        val clientId = "x509_san_dns:verifier.example.com"
        val signingKey = testSigningKey()
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery(
                                id = "pid",
                                format = CredentialFormat.DC_SD_JWT,
                                meta = NoMeta,
                            )
                        )
                    ),
                    signedRequest = true,
                    clientId = clientId,
                    key = signingKey,
                )
            ),
            clientId = clientId,
            urlPrefix = "https://verifier.example.com/verification-session",
            urlHost = "openid4vp://authorize",
            key = signingKey.key,
        )

        val jwt = assertNotNull(session.signedAuthorizationRequestJwt)
        val payload = Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(jwt.split(".")[1]).decodeToString()
        ).jsonObject

        assertEquals(clientId, payload["client_id"]?.jsonPrimitive?.content)
        assertEquals(clientId, payload["iss"]?.jsonPrimitive?.content)
    }

    @Test
    fun `transaction data policies are mandatory when custom vp policies are supplied`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery(
                                id = "pid",
                                format = CredentialFormat.DC_SD_JWT,
                                meta = NoMeta,
                            ),
                            CredentialQuery(
                                id = "mdl",
                                format = CredentialFormat.MSO_MDOC,
                                meta = NoMeta,
                            ),
                        )
                    ),
                    policies = Verification2Session.DefinedVerificationPolicies(
                        vp_policies = VPPolicyList(
                            jwtVcJson = emptyList(),
                            dcSdJwt = listOf(AudienceCheckSdJwtVPPolicy()),
                            msoMdoc = listOf(DeviceAuthMdocVpPolicy()),
                        )
                    )
                ),
                openid = OpenId4VPConfig(
                    transactionData = listOf(
                        transactionDataItem("pid"),
                        transactionDataItem("mdl"),
                    )
                ),
            ),
            clientId = "verifier",
            urlPrefix = "http://localhost/openid4vp",
            urlHost = "http://localhost",
        )

        val vpPolicies = requireNotNull(session.policies.vp_policies)
        val dcSdJwtPolicyIds = vpPolicies.dcSdJwt.map { it.id }
        val mdocPolicyIds = vpPolicies.msoMdoc.map { it.id }

        assertTrue("dc+sd-jwt/audience-check" in dcSdJwtPolicyIds)
        assertTrue("dc+sd-jwt/transaction-data-hash-check" in dcSdJwtPolicyIds)
        assertEquals(
            dcSdJwtPolicyIds.distinct(),
            dcSdJwtPolicyIds,
            "dc+sd-jwt transaction-data policy must not be duplicated",
        )

        assertTrue("mso_mdoc/device-auth" in mdocPolicyIds)
        assertTrue("mso_mdoc/transaction-data-hash-check" in mdocPolicyIds)
        assertEquals(
            mdocPolicyIds.distinct(),
            mdocPolicyIds,
            "mso_mdoc transaction-data policy must not be duplicated",
        )
    }

    @Test
    fun `transaction data policies are not duplicated when caller already supplies them`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery(
                                id = "pid",
                                format = CredentialFormat.DC_SD_JWT,
                                meta = NoMeta,
                            ),
                            CredentialQuery(
                                id = "mdl",
                                format = CredentialFormat.MSO_MDOC,
                                meta = NoMeta,
                            ),
                        )
                    ),
                    policies = Verification2Session.DefinedVerificationPolicies(
                        vp_policies = VPPolicyList(
                            jwtVcJson = emptyList(),
                            dcSdJwt = listOf(
                                AudienceCheckSdJwtVPPolicy(),
                                TransactionDataHashCheckSdJwtVPPolicy(),
                            ),
                            msoMdoc = listOf(
                                DeviceAuthMdocVpPolicy(),
                                TransactionDataMdocVpPolicy(),
                            ),
                        )
                    )
                ),
                openid = OpenId4VPConfig(
                    transactionData = listOf(
                        transactionDataItem("pid"),
                        transactionDataItem("mdl"),
                    )
                ),
            ),
            clientId = "verifier",
            urlPrefix = "http://localhost/openid4vp",
            urlHost = "http://localhost",
        )

        val vpPolicies = requireNotNull(session.policies.vp_policies)
        val dcSdJwtPolicyIds = vpPolicies.dcSdJwt.map { it.id }
        val mdocPolicyIds = vpPolicies.msoMdoc.map { it.id }

        assertEquals(
            1,
            dcSdJwtPolicyIds.count { it == "dc+sd-jwt/transaction-data-hash-check" },
            "dc+sd-jwt transaction-data policy must appear exactly once when caller already supplied it",
        )
        assertEquals(
            1,
            mdocPolicyIds.count { it == "mso_mdoc/transaction-data-hash-check" },
            "mso_mdoc transaction-data policy must appear exactly once when caller already supplied it",
        )
    }

    private fun transactionDataItem(credentialId: String) = buildJsonObject {
        put("type", JsonPrimitive(DEMO_TRANSACTION_DATA_TYPE))
        put("credential_ids", JsonArray(listOf(JsonPrimitive(credentialId))))
        put("require_cryptographic_holder_binding", JsonPrimitive(true))
        put("transaction_data_hashes_alg", JsonArray(listOf(JsonPrimitive("sha-256"))))
    }

    private fun testSigningKey() = DirectSerializedKey(
        KeyManager.resolveSerializedKeyBlocking(
            """{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}"""
        )
    )
}
