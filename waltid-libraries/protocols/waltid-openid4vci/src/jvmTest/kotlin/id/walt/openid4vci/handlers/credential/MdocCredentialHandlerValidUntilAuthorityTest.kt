package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.handlers.endpoints.credential.Crypto2CredentialSigningKey
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceBatch
import id.walt.openid4vci.handlers.endpoints.credential.CredentialIssuanceInput
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.SigningAlgId
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalSerializationApi::class)
class MdocCredentialHandlerValidUntilAuthorityTest {

    @Test
    fun `holder requestForm validUntil does not override configured expiry`() = runTest {
        // Whole-second anchors: MSO tdates truncate, and validFrom must not drift behind signedAt.
        val now = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)
        val configuredValidUntil = now.plus(365.days)
        val expectedUpdate = now.plus(180.days)
        val holderValidUntil = now.plus(3650.days)
        val issuerKey = generateP256Key("mdoc-issuer")
        val certificate = X509CertificateUtil.createSelfSignedCertificate(
            issuerKey,
            SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
        ) {
            subjectDn = "CN=msoData validUntil authority"
        }

        val result = MdocCredentialHandler().sign(
            request = DefaultCredentialRequest(
                client = DefaultClient(
                    id = "test-client",
                    redirectUris = emptyList(),
                    grantTypes = emptySet(),
                    responseTypes = emptySet(),
                ),
                credentialIdentifier = null,
                credentialConfigurationId = DOC_TYPE,
                proofs = null,
                credentialResponseEncryption = null,
                requestForm = mapOf(
                    "validUntil" to listOf(holderValidUntil.toEpochMilliseconds().toString()),
                ),
            ),
            configuration = CredentialConfiguration(
                format = CredentialFormat.MSO_MDOC,
                doctype = DOC_TYPE,
            ),
            issuerKey = Crypto2CredentialSigningKey(
                key = issuerKey,
                algorithm = SigningAlgId.CoseValue(ES256),
            ),
            issuerId = "https://issuer.example",
            issuanceBatch = CredentialIssuanceBatch(
                inputs = listOf(
                    CredentialIssuanceInput(
                        credentialData = buildJsonObject {
                            putJsonObject(DOC_TYPE) {
                                put("given_name", "Jane")
                            }
                        },
                    ),
                ),
                verifiedProofs = listOf(verifiedProof()),
            ),
            dataMapping = buildJsonObject {
                // Top-level validFrom/validUntil are not MSO fields; they must go through msoData.
                // Keep a namespace mapping so we still assert holder/requestForm cannot override MSO expiry
                // while data-function rewrite of claim values still runs.
                putJsonObject(DOC_TYPE) {
                    put("given_name", "<uuid>")
                }
            },
            selectiveDisclosure = null,
            x5Chain = listOf(certificate),
            display = null,
            w3cVersion = null,
            mDocNameSpacesDataMappingConfig = null,
            authorizedTransactionDataTypes = null,
            // Omit validFrom so the issuer uses the issuance/sign instant (avoids signed > validFrom).
            validFrom = null,
            validUntil = configuredValidUntil,
            expectedUpdate = expectedUpdate,
        )

        val success = assertIs<CredentialResponseResult.Success>(result)
        val credential = requireNotNull(success.response.credentials).single().credential.jsonPrimitive.content
        val issuerSigned = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(credential.base64UrlDecode())
        val validity = issuerSigned.decodeMobileSecurityObject().validityInfo

        assertEquals(configuredValidUntil.epochSeconds, validity.validUntil.epochSeconds)
        assertEquals(expectedUpdate.epochSeconds, validity.expectedUpdate?.epochSeconds)
        assertTrue(
            issuerSigned.namespacesToJson().getValue(DOC_TYPE).jsonObject.getValue("given_name")
                .jsonPrimitive.content.startsWith("urn:uuid:"),
        )
    }

    private suspend fun verifiedProof() = VerifiedCredentialProof(
        proofType = "jwt",
        jwt = "",
        algorithm = "ES256",
        header = buildJsonObject { },
        payload = buildJsonObject { },
        holderKey = generateP256Key("mdoc-holder"),
        holderKid = null,
        holderDid = null,
        nonce = null,
    )

    private suspend fun generateP256Key(id: String) = crypto2Runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )

    private companion object {
        const val DOC_TYPE = "org.iso.18013.5.1.mDL"
        const val ES256 = -7
        val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    }
}
