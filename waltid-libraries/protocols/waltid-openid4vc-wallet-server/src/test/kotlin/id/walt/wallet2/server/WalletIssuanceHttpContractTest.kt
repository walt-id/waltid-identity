package id.walt.wallet2.server

import id.walt.openid4vci.prooftypes.Proofs
import id.walt.wallet2.handlers.*
import io.ktor.http.Url
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletIssuanceHttpContractTest {
    @Test
    fun singleAuthorizedTargetNormalizesAtThePublicBoundaryAndRejectsAmbiguity() {
        val body = """{"code":"code","credentialIssuer":"https://issuer.example",
            "credentialEndpoint":"https://issuer.example/credential","credentialConfigurationId":"identity"}"""
        val request = Json.decodeFromString<ReceiveAuthorizedCredentialRequest>(body)
        assertEquals(listOf(WalletCredentialSelection("identity")), request.selectedCredentials())
        assertFailsWith<IllegalArgumentException> {
            request.copy(credentials = listOf(WalletCredentialSelection("other")))
        }
        assertFailsWith<IllegalArgumentException> { request.copy(credentialConfigurationId = null) }
        val batch = request.copy(credentialConfigurationId = null,
            credentials = listOf(WalletCredentialSelection("identity"), WalletCredentialSelection("other")))
        assertEquals(listOf("identity", "other"), batch.selectedCredentials().map { it.credentialConfigurationId })
    }

    @Test
    fun isolatedSingleProofNormalizesWithoutChangingTheProtocolShape() {
        val request = Json.decodeFromString<FetchCredentialRequest>(
            """{"credentialEndpoint":"https://issuer.example/credential","accessToken":"token",
                "credentialConfigurationId":"identity","proofJwt":"single-proof"}""")
        assertEquals(listOf("single-proof"), request.effectiveProofs?.jwt)
        assertFailsWith<IllegalArgumentException> { request.copy(proofs = Proofs(jwt = listOf("other-proof"))) }
        val single = Json.encodeToJsonElement(SignProofResult(Proofs(jwt = listOf("single-proof")))).jsonObject
        assertEquals("single-proof", single["proofJwt"]?.jsonPrimitive?.content)
        val batch = Json.encodeToJsonElement(SignProofResult(Proofs(jwt = listOf("one", "two")))).jsonObject
        assertEquals(JsonNull, batch["proofJwt"])
        assertEquals(2, batch.getValue("proofs").jsonObject.getValue("jwt").jsonArray.size)
    }

    @Test
    fun legacyResponseProjectionsNeverDropRepeatedDatasetsOrConfigurations() {
        val targets = listOf(
            DeferredCredentialTransaction("identity", "dataset-a", "tx-a", listOf(CredentialHolderBinding("key-a")), 5),
            DeferredCredentialTransaction("identity", "dataset-b", "tx-b", listOf(CredentialHolderBinding("key-b")), 5),
        )
        val single = Json.encodeToJsonElement(ReceiveCredentialResult(emptyList(), targets.take(1))).jsonObject
        assertEquals("tx-a", single.getValue("deferredTransactionIds").jsonObject["identity"]?.jsonPrimitive?.content)
        val batch = Json.encodeToJsonElement(ReceiveCredentialResult(emptyList(), targets)).jsonObject
        assertEquals(JsonNull, batch["deferredTransactionIds"])
        assertEquals(2, batch.getValue("deferredCredentials").jsonArray.size)
        val authorization = GenerateAuthorizationUrlResult(Url("https://issuer.example/authorize"), "state",
            credentialConfigurationIds = listOf("identity"), credentialIssuerBaseUrl = "https://issuer.example")
        assertEquals("identity", Json.encodeToJsonElement(authorization).jsonObject["credentialConfigurationId"]?.jsonPrimitive?.content)
        val multiple = authorization.copy(credentialConfigurationIds = listOf("identity", "other"))
        assertEquals(JsonNull, Json.encodeToJsonElement(multiple).jsonObject["credentialConfigurationId"])
    }
}
