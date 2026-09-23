package id.walt.openid4vci.handlers.credential

import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.openid4vci.DefaultClient
import id.walt.openid4vci.proofs.VerifiedCredentialProof
import id.walt.openid4vci.requests.credential.DefaultCredentialRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the MSO `KeyAuthorizations` that issuance embeds for transaction data. The verifier-side
 * transaction data tests hand-build an already authorized MSO, so they never exercise this path.
 */
@OptIn(ExperimentalSerializationApi::class)
class MdocCredentialSignerKeyAuthorizationsTest {

    @Test
    fun `authorized transaction data types become granular data element authorizations`() = runTest {
        val keyAuthorizations = requireNotNull(issueMdoc(listOf(SCA_PAYMENT_TYPE))) {
            "Expected keyAuthorizations for an mdoc issued with authorizedTransactionDataTypes"
        }

        // The wallet only ever device-signs the two hash elements, so the whole namespace must not
        // be granted. ISO 18013-5 also forbids a namespace appearing in both fields.
        assertNull(keyAuthorizations.namespaces, "Expected no blanket nameSpaces grant")
        assertEquals(
            mapOf(SCA_PAYMENT_TYPE to TRANSACTION_DATA_HASH_ELEMENTS),
            keyAuthorizations.dataElements,
        )
    }

    @Test
    fun `each authorized type is authorized separately`() = runTest {
        val dataElements = requireNotNull(
            issueMdoc(listOf(SCA_PAYMENT_TYPE, PAYMENT_AUTHORIZATION_TYPE))?.dataElements
        )

        assertEquals(
            mapOf(
                SCA_PAYMENT_TYPE to TRANSACTION_DATA_HASH_ELEMENTS,
                PAYMENT_AUTHORIZATION_TYPE to TRANSACTION_DATA_HASH_ELEMENTS,
            ),
            dataElements,
        )
    }

    @Test
    fun `blank and duplicate types are dropped`() = runTest {
        val dataElements = issueMdoc(listOf(SCA_PAYMENT_TYPE, SCA_PAYMENT_TYPE, "", "   "))?.dataElements

        assertEquals(mapOf(SCA_PAYMENT_TYPE to TRANSACTION_DATA_HASH_ELEMENTS), dataElements)
    }

    @Test
    fun `mdocs without authorized transaction data types carry no key authorizations`() = runTest {
        assertNull(issueMdoc(null))
        assertNull(issueMdoc(emptyList()))
        assertNull(issueMdoc(listOf("", "  ")))
    }

    private suspend fun issueMdoc(authorizedTransactionDataTypes: List<String>?) =
        MdocCredentialSigner.generateMdocCredential(
            credentialRequest = credentialRequest(),
            credentialData = buildJsonObject {
                putJsonObject(SCA_DOC_TYPE) {
                    put("card_scheme", "visa")
                    put("card_last4", "4242")
                }
            },
            issuerKey = generateP256Key("mdoc-issuer"),
            signatureAlgorithm = ES256,
            issuerCertificate = listOf(CoseCertificate(byteArrayOf(1, 2, 3))),
            docType = SCA_DOC_TYPE,
            verifiedProof = verifiedProof(),
            authorizedTransactionDataTypes = authorizedTransactionDataTypes,
        ).let { credential ->
            coseCompliantCbor.decodeFromByteArray<IssuerSigned>(credential.base64UrlDecode())
                .decodeMobileSecurityObject()
                .deviceKeyInfo
                .keyAuthorizations
        }

    private fun credentialRequest() = DefaultCredentialRequest(
        client = DefaultClient(
            id = "test-client",
            redirectUris = emptyList(),
            grantTypes = emptySet(),
            responseTypes = emptySet(),
        ),
        credentialIdentifier = null,
        credentialConfigurationId = SCA_DOC_TYPE,
        proofs = null,
        credentialResponseEncryption = null,
    )

    // Supplying the verified proof directly keeps the test on the signer, not on proof validation.
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
        const val SCA_DOC_TYPE = "eu.europa.ec.eudi.sca.payment_card.1"
        const val SCA_PAYMENT_TYPE = "urn:eudi:sca:payment:1"
        const val PAYMENT_AUTHORIZATION_TYPE = "org.waltid.transaction-data.payment-authorization"
        const val ES256 = -7

        val TRANSACTION_DATA_HASH_ELEMENTS = listOf("transaction_data_hash", "transaction_data_hash_alg")

        val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    }
}
