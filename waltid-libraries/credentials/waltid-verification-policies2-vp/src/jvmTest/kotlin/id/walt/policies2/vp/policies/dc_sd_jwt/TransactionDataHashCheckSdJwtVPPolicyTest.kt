@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.credentials.presentations.formats.DcSdJwtPresentation
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.DEFAULT_HASH_ALGORITHM
import id.walt.verifier.openid.transactiondata.calculateTransactionDataHashes
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransactionDataHashCheckSdJwtVPPolicyTest {

    private val policy = TransactionDataHashCheckSdJwtVPPolicy()
    private val samplePresentationToken by lazy { loadFixture("fixtures/dc_sd_jwt/presentation.jwt") }

    @Test
    fun `succeeds when presentation hashes match requested transaction data`() = runTest {
        val transactionData = transactionData()
        val presentation = samplePresentation().copy(
            transactionDataHashes = calculateTransactionDataHashes(listOf(transactionData)),
            transactionDataHashesAlg = DEFAULT_HASH_ALGORITHM,
        )

        val result = policy.runPolicy(
            presentation = presentation,
            verificationContext = verificationContext(expectedTransactionData = listOf(transactionData)),
        )

        assertTrue(result.success)
    }

    @Test
    fun `fails when presentation hashes do not match requested transaction data`() = runTest {
        val transactionData = transactionData()
        val presentation = samplePresentation().copy(
            transactionDataHashes = listOf("invalid-hash"),
            transactionDataHashesAlg = DEFAULT_HASH_ALGORITHM,
        )

        val result = policy.runPolicy(
            presentation = presentation,
            verificationContext = verificationContext(expectedTransactionData = listOf(transactionData)),
        )

        assertFalse(result.success)
        assertTrue(result.errors.any { it.message?.contains("transaction_data_hashes", ignoreCase = true) == true })
    }

    @Test
    fun `fails when response algorithm is omitted but request declared transaction_data_hashes_alg`() = runTest {
        val transactionData = transactionData()
        val presentation = samplePresentation().copy(
            transactionDataHashes = calculateTransactionDataHashes(listOf(transactionData)),
            transactionDataHashesAlg = null,
        )

        val result = policy.runPolicy(
            presentation = presentation,
            verificationContext = verificationContext(expectedTransactionData = listOf(transactionData)),
        )

        assertFalse(result.success)
        assertTrue(result.errors.any { it.message?.contains("transaction_data_hashes_alg", ignoreCase = true) == true })
    }

    @Test
    fun `fails when transaction_data_hashes are present without transaction_data request`() = runTest {
        val presentation = samplePresentation().copy(
            transactionDataHashes = emptyList(),
            transactionDataHashesAlg = null,
        )

        val result = policy.runPolicy(
            presentation = presentation,
            verificationContext = verificationContext(expectedTransactionData = null),
        )

        assertFalse(result.success)
        assertTrue(result.errors.any { it.message?.contains("transaction_data_hashes", ignoreCase = true) == true })
    }

    private fun verificationContext(expectedTransactionData: List<String>? = null) = VerificationSessionContext(
        vpToken = "vp_token",
        expectedNonce = "3c04c5fc-9306-40fa-b544-0e00474ace09",
        expectedAudience = "x509_san_dns:test123",
        expectedOrigins = null,
        expectedTransactionData = expectedTransactionData,
        responseUri = null,
        responseMode = OpenID4VPResponseMode.DIRECT_POST,
        isSigned = true,
        isEncrypted = false,
        jwkThumbprint = null,
        isAnnexC = false,
        customData = null,
    )

    private suspend fun samplePresentation(): DcSdJwtPresentation =
        DcSdJwtPresentation.parse(
            samplePresentationToken
        ).getOrThrow()

    private fun loadFixture(path: String): String = checkNotNull(
        javaClass.classLoader.getResource(path)
    ) { "Missing fixture: $path" }.readText().trim()

    private fun transactionData(): String = buildJsonObject {
        put("type", "org.waltid.transaction-data.payment-authorization")
        put("credential_ids", buildJsonArray {
            add(JsonPrimitive("payment_credential"))
        })
        put("require_cryptographic_holder_binding", true)
        put("transaction_data_hashes_alg", buildJsonArray {
            add(JsonPrimitive(DEFAULT_HASH_ALGORITHM))
        })
        put("amount", "42.00")
        put("currency", "EUR")
        put("payee", "ACME Corp")
    }.toString().encodeToByteArray().encodeToBase64Url()
}
