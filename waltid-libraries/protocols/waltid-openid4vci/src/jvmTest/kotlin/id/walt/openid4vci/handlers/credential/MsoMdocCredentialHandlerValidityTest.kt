package id.walt.openid4vci.handlers.credential

import id.walt.certificate.x509.X509Certificate
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import id.walt.openid4vci.responses.credential.CredentialResponseResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import id.walt.crypto2.keys.Key as Crypto2Key

class MsoMdocCredentialHandlerValidityTest {

    @Test
    fun `sign forwards resolved validity instants to issueMdoc`() = runTest {
        val now = Clock.System.now()
        val validFrom = now
        val validUntil = now.plus(365.days)
        val expectedUpdate = now.plus(180.days)
        val handler = RecordingMsoMdocCredentialHandler()

        val result = handler.sign(
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
            ),
            configuration = CredentialConfiguration(
                format = CredentialFormat.MSO_MDOC,
                doctype = DOC_TYPE,
            ),
            issuerKey = JWKKey.generate(KeyType.secp256r1),
            issuerId = "https://issuer.example",
            credentialData = buildJsonObject {
                putJsonObject(DOC_TYPE) {
                    put("given_name", "Jane")
                }
            },
            dataMapping = null,
            selectiveDisclosure = null,
            x5Chain = null,
            display = null,
            w3cVersion = null,
            mDocNameSpacesDataMappingConfig = null,
            authorizedTransactionDataTypes = null,
            credentialStatus = null,
            validFrom = validFrom,
            validUntil = validUntil,
            expectedUpdate = expectedUpdate,
            verifiedProofs = listOf(verifiedProof()),
        )

        assertIs<CredentialResponseResult.Success>(result)
        assertEquals(validFrom, handler.capturedValidFrom)
        assertEquals(validUntil, handler.capturedValidUntil)
        assertEquals(expectedUpdate, handler.capturedExpectedUpdate)
        assertNull(handler.capturedValidityDays.takeIf { it != 365 })
        assertEquals(365, handler.capturedValidityDays)
    }

    private class RecordingMsoMdocCredentialHandler : MsoMdocCredentialHandler() {
        var capturedValidFrom: Instant? = Instant.DISTANT_PAST
        var capturedValidUntil: Instant? = Instant.DISTANT_PAST
        var capturedExpectedUpdate: Instant? = Instant.DISTANT_PAST
        var capturedValidityDays: Int = -1

        override suspend fun issueMdoc(
            docType: String,
            namespaceData: Map<String, JsonObject>,
            holderKey: Crypto2Key,
            issuerKey: Key,
            x5Chain: List<X509Certificate>?,
            validFrom: Instant?,
            validUntil: Instant?,
            expectedUpdate: Instant?,
            validityDays: Int,
        ): String {
            capturedValidFrom = validFrom
            capturedValidUntil = validUntil
            capturedExpectedUpdate = expectedUpdate
            capturedValidityDays = validityDays
            return "issued"
        }
    }

    private suspend fun verifiedProof() = VerifiedCredentialProof(
        proofType = "jwt",
        jwt = "",
        algorithm = "ES256",
        header = buildJsonObject { },
        payload = buildJsonObject { },
        holderKey = crypto2Runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("mdoc-holder"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        ),
        holderKid = null,
        holderDid = null,
        nonce = null,
    )

    private companion object {
        const val DOC_TYPE = "org.iso.18013.5.1.mDL"
        val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    }
}
