package id.walt.verifier.openid.transactiondata

import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransactionDataRequestValidatorTest {
    private val supportedType = TransactionDataTestFixtures.SUPPORTED_TRANSACTION_DATA_TYPE

    @Test
    fun `accepts supported payment data`() {
        val encoded = TransactionDataTestFixtures.transactionData()

        val decoded = validateRequestTransactionData(
            transactionData = listOf(encoded),
            supportedTypes = setOf(supportedType),
            credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(),
        )

        assertEquals(supportedType, decoded.single().transactionData.type)
        assertEquals("42.00", decoded.single().details["amount"]?.toString()?.trim('"'))
    }

    @Test
    fun `rejects unsupported transaction data type`() {
        val encoded = TransactionDataTestFixtures.transactionData(type = "unsupported-type")

        assertFailsWith<IllegalArgumentException> {
            validateRequestTransactionData(
                transactionData = listOf(encoded),
                supportedTypes = setOf(supportedType),
                credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(),
            )
        }
    }

    @Test
    fun `accepts mdoc credential queries`() {
        val encoded = TransactionDataTestFixtures.transactionData()

        val decoded = validateRequestTransactionData(
            transactionData = listOf(encoded),
            supportedTypes = setOf(supportedType),
            credentialQueriesById = mapOf(
                "payment_credential" to CredentialQuery(
                    id = "payment_credential",
                    format = CredentialFormat.MSO_MDOC,
                    meta = MsoMdocMeta(doctypeValue = "org.iso.18013.5.1.mDL"),
                ),
            ),
        )

        assertEquals(encoded, decoded.single().encoded)
    }

    @Test
    fun `rejects unsupported credential query formats`() {
        val encoded = TransactionDataTestFixtures.transactionData()

        assertFailsWith<IllegalArgumentException> {
            validateRequestTransactionData(
                transactionData = listOf(encoded),
                supportedTypes = setOf(supportedType),
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
    fun `rejects credential queries without holder binding`() {
        val encoded = TransactionDataTestFixtures.transactionData()

        assertFailsWith<IllegalArgumentException> {
            validateRequestTransactionData(
                transactionData = listOf(encoded),
                supportedTypes = setOf(supportedType),
                credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(
                    requireCryptographicHolderBinding = false,
                ),
            )
        }
    }

    @Test
    fun `rejects missing holder binding requirement`() {
        val encoded = TransactionDataTestFixtures.transactionDataWithoutHolderBindingRequirement()

        assertFailsWith<IllegalArgumentException> {
            validateRequestTransactionData(
                transactionData = listOf(encoded),
                supportedTypes = setOf(supportedType),
                credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(),
            )
        }
    }
}
