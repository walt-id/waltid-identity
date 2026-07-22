@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2.handlers.sessioncreation

import id.walt.cose.Cose
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeyManager
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.policies2.vp.policies.AudienceCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.DeviceAuthMdocVpPolicy
import id.walt.policies2.vp.policies.TransactionDataHashCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.TransactionDataHashesVPPolicy
import id.walt.policies2.vp.policies.TransactionDataMdocVpPolicy
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.OpenId4VPConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val DEMO_TRANSACTION_DATA_TYPE = "org.waltid.transaction-data.payment-authorization"

class VerificationSessionCreatorTransactionDataPolicyTest {

    @Test
    fun `SIOP setup is wired into authorization request and metadata`() = runTest {
        val session = VerificationSessionCreator.createVerificationSession(
            setup = CrossDeviceFlowSetup(
                core = GeneralFlowConfig(
                    dcqlQuery = DcqlQuery(
                        credentials = listOf(
                            CredentialQuery("pid", CredentialFormat.DC_SD_JWT, meta = NoMeta)
                        )
                    )
                ),
                openid = OpenId4VPConfig(
                    responseType = OpenID4VPResponseType.VP_TOKEN_ID_TOKEN,
                    scope = "openid",
                    idTokenType = "subject_signed",
                ),
            ),
            clientId = "verifier",
            urlPrefix = "https://verifier.example/verification-session",
            urlHost = "openid4vp://authorize",
        )

        assertEquals(OpenID4VPResponseType.VP_TOKEN_ID_TOKEN, session.authorizationRequest.responseType)
        assertEquals("openid", session.authorizationRequest.scope)
        assertEquals("subject_signed", session.authorizationRequest.idTokenType)
        assertEquals("RS256", session.authorizationRequest.clientMetadata?.idTokenSignedResponseAlg)
        assertTrue(session.authorizationRequest.clientMetadata?.subjectSyntaxTypesSupported?.isNotEmpty() == true)
    }

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
    fun `signed authorization request uses crypto2 key`() = runTest {
        val key = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("verifier-request-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val clientId = "verifier.example"
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
                    expirationDate = Clock.System.now() + 5.minutes,
                )
            ),
            clientId = clientId,
            urlPrefix = "https://verifier.example/verification-session",
            urlHost = "openid4vp://authorize",
            key = key,
            jwsAlgorithm = JwsAlgorithm.ES256,
            coseAlgorithm = Cose.Algorithm.ES256,
            signingKeyReference = "tenant.kms.verifier-key",
        )

        val verified = CompactJws.verify(assertNotNull(session.signedAuthorizationRequestJwt), key, JwsAlgorithm.ES256)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
        assertEquals("https://self-issued.me/v2", payload["aud"]?.jsonPrimitive?.content)
        assertEquals("oauth-authz-req+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertNotNull(payload["iat"])
        assertNotNull(payload["exp"])
        assertEquals(null, verified.protectedHeader["iat"])
        assertEquals(null, verified.protectedHeader["exp"])
        assertEquals("tenant.kms.verifier-key", session.requestSigningKeyReference)
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
    @Suppress("DEPRECATION")
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
                                TransactionDataHashesVPPolicy(),
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
            0,
            dcSdJwtPolicyIds.count { it == "dc+sd-jwt/transaction-data-hash-check" },
            "canonical dc+sd-jwt policy must not be added when the legacy alias is already supplied",
        )
        assertEquals(
            1,
            dcSdJwtPolicyIds.count { it == TransactionDataHashesVPPolicy.ID },
            "legacy dc+sd-jwt transaction-data policy must remain exactly once",
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
