package id.walt.verifier.openid

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.dcql.models.meta.SdJwtVcMeta
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionDataUtilsTest {
    private val supportedTransactionDataType = TransactionDataUtils.DEMO_TRANSACTION_DATA_TYPE

    @Test
    fun `validateRequestTransactionData accepts supported payment data`() {
        val encoded = transactionData(
            type = supportedTransactionDataType,
            amount = "42.00",
            payee = "ACME Corp",
        )

        val decoded = TransactionDataUtils.validateRequestTransactionData(
            transactionData = listOf(encoded),
            supportedTypes = setOf(supportedTransactionDataType),
            credentialQueriesById = sdJwtCredentialQueries(),
        )

        assertEquals(supportedTransactionDataType, decoded.single().transactionData.type)
        assertEquals("42.00", decoded.single().details["amount"]?.toString()?.trim('"'))
    }

    @Test
    fun `validateRequestTransactionData rejects unsupported transaction data type`() {
        val encoded = transactionData(
            type = "unsupported-type",
            amount = "42.00",
            payee = "ACME Corp",
        )

        assertFailsWith<IllegalArgumentException> {
            TransactionDataUtils.validateRequestTransactionData(
                transactionData = listOf(encoded),
                supportedTypes = setOf(supportedTransactionDataType),
                credentialQueriesById = sdJwtCredentialQueries(),
            )
        }
    }

    @Test
    fun `validateRequestTransactionData accepts mdoc credential queries`() {
        val encoded = transactionData(
            type = supportedTransactionDataType,
            amount = "42.00",
            payee = "ACME Corp",
        )

        val decoded = TransactionDataUtils.validateRequestTransactionData(
            transactionData = listOf(encoded),
            supportedTypes = setOf(supportedTransactionDataType),
            credentialQueriesById = mapOf(
                "payment_credential" to CredentialQuery(
                    id = "payment_credential",
                    format = CredentialFormat.MSO_MDOC,
                    meta = MsoMdocMeta(
                        doctypeValue = "org.iso.18013.5.1.mDL",
                    ),
                ),
            ),
        )

        assertEquals(encoded, decoded.single().encoded)
    }

    @Test
    fun `validateRequestTransactionData rejects unsupported credential query formats`() {
        val encoded = transactionData(
            type = supportedTransactionDataType,
            amount = "42.00",
            payee = "ACME Corp",
        )

        assertFailsWith<IllegalArgumentException> {
            TransactionDataUtils.validateRequestTransactionData(
                transactionData = listOf(encoded),
                supportedTypes = setOf(supportedTransactionDataType),
                credentialQueriesById = mapOf(
                    "payment_credential" to CredentialQuery(
                        id = "payment_credential",
                        format = CredentialFormat.JWT_VC_JSON,
                        meta = JwtVcJsonMeta(
                            typeValues = listOf(listOf("VerifiableCredential", "PaymentCredential")),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `filterTransactionDataForCredentialId keeps only matching items`() {
        val paymentCredential = transactionData(
            type = supportedTransactionDataType,
            amount = "42.00",
            payee = "ACME Corp",
        )
        val loyaltyCredential = buildJsonObject {
            put("type", supportedTransactionDataType)
            put("credential_ids", buildJsonArray {
                add(JsonPrimitive("loyalty_credential"))
            })
            put("require_cryptographic_holder_binding", true)
            put("amount", "5.00")
            put("payee", "Coffee Shop")
            put("currency", "EUR")
        }.toString().encodeToByteArray().encodeToBase64Url()

        val filtered = TransactionDataUtils.filterTransactionDataForCredentialId(
            transactionData = listOf(paymentCredential, loyaltyCredential),
            credentialId = "payment_credential",
        )

        assertEquals(listOf(paymentCredential), filtered)
    }

    @Test
    fun `mdoc embedded transaction data roundtrips`() {
        val encodedItems = listOf(
            transactionData(type = supportedTransactionDataType, amount = "42.00", payee = "ACME Corp"),
            transactionData(type = supportedTransactionDataType, amount = "5.00", payee = "Coffee Shop"),
        )

        val extracted = TransactionDataUtils.extractMdocEmbeddedTransactionData(
            TransactionDataUtils.buildMdocEmbeddedTransactionData(encodedItems)
        )

        assertEquals(encodedItems, extracted)
    }

    @Test
    fun `validateRequestTransactionData rejects credential queries without holder binding`() {
        val encoded = transactionData(
            type = supportedTransactionDataType,
            amount = "42.00",
            payee = "ACME Corp",
        )

        assertFailsWith<IllegalArgumentException> {
            TransactionDataUtils.validateRequestTransactionData(
                transactionData = listOf(encoded),
                supportedTypes = setOf(supportedTransactionDataType),
                credentialQueriesById = mapOf(
                    "payment_credential" to CredentialQuery(
                        id = "payment_credential",
                        format = CredentialFormat.DC_SD_JWT,
                        meta = SdJwtVcMeta(
                            vctValues = listOf("https://issuer.example/payment_credential"),
                        ),
                        requireCryptographicHolderBinding = false,
                    ),
                ),
            )
        }
    }

    @Test
    fun `validateRequestTransactionData rejects missing holder binding requirement`() {
        val encoded = buildJsonObject {
            put("type", supportedTransactionDataType)
            put("credential_ids", buildJsonArray {
                add(JsonPrimitive("payment_credential"))
            })
            put("amount", "42.00")
            put("payee", "ACME Corp")
            put("currency", "EUR")
        }.toString().encodeToByteArray().encodeToBase64Url()

        assertFailsWith<IllegalArgumentException> {
            TransactionDataUtils.validateRequestTransactionData(
                transactionData = listOf(encoded),
                supportedTypes = setOf(supportedTransactionDataType),
                credentialQueriesById = sdJwtCredentialQueries(),
            )
        }
    }

    @Test
    fun `validateResponseTransactionData verifies sha-256 hashes over encoded values`() {
        val encoded = transactionData(
            type = supportedTransactionDataType,
            amount = "42.00",
            payee = "ACME Corp",
            hashAlgorithms = listOf("sha-256"),
        )
        val hashes = TransactionDataUtils.calculateTransactionDataHashes(listOf(encoded))

        TransactionDataUtils.validateResponseTransactionData(
            expectedTransactionData = listOf(encoded),
            transactionDataHashes = hashes,
            transactionDataHashesAlg = "sha-256",
        )
    }

    @Test
    fun `validateResponseTransactionData requires an explicit response algorithm when the request declares one`() {
        val encoded = transactionData(
            type = supportedTransactionDataType,
            amount = "42.00",
            payee = "ACME Corp",
            hashAlgorithms = listOf("sha-256"),
        )

        val error = assertFailsWith<TransactionDataUtils.TransactionDataValidationException> {
            TransactionDataUtils.validateResponseTransactionData(
                expectedTransactionData = listOf(encoded),
                transactionDataHashes = TransactionDataUtils.calculateTransactionDataHashes(listOf(encoded)),
                transactionDataHashesAlg = null,
            )
        }

        assertEquals(
            TransactionDataUtils.TransactionDataValidationErrorReason.HASH_ALGORITHM_MISMATCH,
            error.reason,
        )
    }

    private fun transactionData(
        type: String,
        amount: String,
        payee: String,
        hashAlgorithms: List<String>? = null,
    ): String = buildJsonObject {
        put("type", type)
        put("credential_ids", buildJsonArray {
            add(JsonPrimitive("payment_credential"))
        })
        put("require_cryptographic_holder_binding", true)
        if (hashAlgorithms != null) {
            put("transaction_data_hashes_alg", buildJsonArray {
                hashAlgorithms.forEach { add(JsonPrimitive(it)) }
            })
        }
        put("amount", amount)
        put("payee", payee)
        put("currency", "EUR")
    }.toString().encodeToByteArray().encodeToBase64Url()

    private fun sdJwtCredentialQueries() = mapOf(
        "payment_credential" to CredentialQuery(
            id = "payment_credential",
            format = CredentialFormat.DC_SD_JWT,
            meta = SdJwtVcMeta(
                vctValues = listOf("https://issuer.example/payment_credential"),
            ),
        ),
    )
}
