package id.walt.issuer2.openid4vci

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.issuer2.testsupport.Issuer2CredentialScenario
import id.walt.issuer2.testsupport.Issuer2CredentialScenarios
import id.walt.issuer2.testsupport.Issuer2TxCodeMode
import id.walt.issuer2.testsupport.Issuer2WalletFlowDriver
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createWalletFlowCredentialOffer
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.mso.KeyAuthorization
import id.walt.openid4vci.offers.AuthenticationMethod
import io.ktor.client.HttpClient
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Transaction data presentation is gated on the MSO's `KeyAuthorizations`, so an mdoc issued
 * without it can never sign transaction data. These tests pin that grant at the issuer2 boundary:
 * a profile that authorizes a type gets exactly the hash elements, and one that does not gets nothing.
 */
class Issuer2TransactionDataAuthorizationTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun scaPaymentCardProfileAuthorizesOnlyTheTransactionDataHashElements() = testApplication {
        installIssuer2WithConfigFiles()

        val keyAuthorizations = assertNotNull(
            apiClient().issueAndReadKeyAuthorizations(),
            "Expected keyAuthorizations for a profile with authorizedTransactionDataTypes",
        )

        // A blanket nameSpaces grant would let the device key sign anything under this namespace,
        // while the wallet only ever device-signs the two transaction data hash elements.
        assertNull(keyAuthorizations.namespaces, "Expected no blanket nameSpaces grant")
        assertEquals(
            mapOf(SCA_PAYMENT_TYPE to TRANSACTION_DATA_HASH_ELEMENTS),
            keyAuthorizations.dataElements,
        )
    }

    @Test
    fun profilesWithoutAuthorizedTransactionDataTypesCarryNoKeyAuthorizations() = testApplication {
        installIssuer2WithConfigFiles()

        val keyAuthorizations = apiClient().issueAndReadKeyAuthorizations(
            scenario = Issuer2CredentialScenarios.isoMdl,
        )

        assertNull(
            keyAuthorizations,
            "Expected no keyAuthorizations for a profile without authorizedTransactionDataTypes",
        )
    }

    private suspend fun HttpClient.issueAndReadKeyAuthorizations(
        scenario: Issuer2CredentialScenario = scaPaymentCardScenario,
    ): KeyAuthorization? {
        val walletFlow = Issuer2WalletFlowDriver(this)
        val createdOffer = createWalletFlowCredentialOffer(
            scenario = scenario,
            authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED,
            txCodeMode = Issuer2TxCodeMode.NONE,
        )

        val resolvedOffer = walletFlow.resolve(createdOffer)
        val tokenResponse = walletFlow.exchangePreAuthorizedCode(resolvedOffer, txCode = null)
        val credentialPayload = walletFlow.requestCredential(
            resolvedOffer = resolvedOffer,
            accessToken = tokenResponse.access_token,
        )

        return credentialPayload.decodeMso().deviceKeyInfo.keyAuthorizations
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun JsonObject.decodeMso() = assertNotNull(
        this["credentials"]?.jsonArray?.single()?.jsonObject?.get("credential")?.jsonPrimitive?.content,
        "Expected an issued credential in the credential response",
    ).let { credential ->
        coseCompliantCbor.decodeFromByteArray<IssuerSigned>(credential.decodeFromBase64Url())
            .decodeMobileSecurityObject()
    }

    private companion object {
        const val SCA_PROFILE_ID = "scaPaymentCardMdoc"
        const val SCA_PAYMENT_TYPE = "urn:eudi:sca:payment:1"

        val TRANSACTION_DATA_HASH_ELEMENTS = listOf("transaction_data_hash", "transaction_data_hash_alg")

        val scaPaymentCardScenario =
            Issuer2CredentialScenarios.configured.single { it.profileId == SCA_PROFILE_ID }
    }
}
