package id.walt.verifier.openid.transactiondata

import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfile
import id.walt.verifier.openid.transactiondata.profile.TransactionDataTypeProfileRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private object TestTransactionDataProfile : TransactionDataTypeProfile(
    type = TransactionDataTestFixtures.SUPPORTED_TRANSACTION_DATA_TYPE,
    displayName = "Test Profile",
)

class TransactionDataRequestValidatorTest {
    private val registry = TransactionDataTypeProfileRegistry(TestTransactionDataProfile)

    @Test
    fun `accepts supported payment data`() {
        val encoded = TransactionDataTestFixtures.transactionData()

        val decoded = validateRequestTransactionData(
            transactionData = listOf(encoded),
            profileRegistry = registry,
            credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(),
        )

        assertEquals(TransactionDataTestFixtures.SUPPORTED_TRANSACTION_DATA_TYPE, decoded.single().transactionData.type)
        assertEquals("42.00", decoded.single().details["amount"]?.toString()?.trim('"'))
    }

    @Test
    fun `rejects unknown type when registry is non-empty`() {
        val encoded = TransactionDataTestFixtures.transactionData(type = "unsupported-type")

        assertFailsWith<IllegalArgumentException> {
            validateRequestTransactionData(
                transactionData = listOf(encoded),
                profileRegistry = registry,
                credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(),
            )
        }
    }

    @Test
    fun `skips profile validation when registry is empty`() {
        val encoded = TransactionDataTestFixtures.transactionData(type = "unsupported-type")

        val decoded = validateRequestTransactionData(
            transactionData = listOf(encoded),
            credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(),
        )

        assertEquals("unsupported-type", decoded.single().transactionData.type)
    }

    @Test
    fun `accepts mdoc credential queries`() {
        val encoded = TransactionDataTestFixtures.transactionData()

        val decoded = validateRequestTransactionData(
            transactionData = listOf(encoded),
            profileRegistry = registry,
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
                profileRegistry = registry,
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
                profileRegistry = registry,
                credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(
                    requireCryptographicHolderBinding = false,
                ),
            )
        }
    }

    @Test
    fun `accepts missing transaction data holder binding requirement`() {
        val encoded = TransactionDataTestFixtures.transactionDataWithoutHolderBindingRequirement()

        val decoded = validateRequestTransactionData(
            transactionData = listOf(encoded),
            profileRegistry = registry,
            credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(),
        )

        assertEquals(encoded, decoded.single().encoded)
    }

    @Test
    fun `rejects explicit false transaction data holder binding requirement`() {
        val encoded = TransactionDataTestFixtures.transactionData(requireCryptographicHolderBinding = false)

        assertFailsWith<IllegalArgumentException> {
            validateRequestTransactionData(
                transactionData = listOf(encoded),
                profileRegistry = registry,
                credentialQueriesById = TransactionDataTestFixtures.sdJwtCredentialQueries(),
            )
        }
    }
}
