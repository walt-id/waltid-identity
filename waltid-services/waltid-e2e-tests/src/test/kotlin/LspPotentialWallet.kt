import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.webwallet.db.models.WalletDid
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.uuid.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

class LspPotentialWallet(val client: HttpClient, val walletId: String) {

  fun testMDocIssuance() = runBlocking {
    // === get credential offer from test issuer API ===
    val offerResp = client.get("/lsp-potential/lspPotentialCredentialOfferT1")
    assert(offerResp.status == HttpStatusCode.OK)
    val offerUri = offerResp.bodyAsText()

    // === resolve credential offer ===
    val resolvedOffer = client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
      setBody(offerUri)
    }.expectSuccess().let {
      it.body<CredentialOffer>()
    }
    assertEquals(1, resolvedOffer.credentialConfigurationIds.size)
    assertEquals("org.iso.18013.5.1.mDL", resolvedOffer.credentialConfigurationIds.first())

    // === resolve issuer metadata ===
    val issuerMetadata = client.get("${resolvedOffer.credentialIssuer}/.well-known/openid-credential-issuer").expectSuccess().let {
      it.body<OpenIDProviderMetadata>()
    }
    assertEquals(issuerMetadata.issuer, resolvedOffer.credentialIssuer)
    assertContains(issuerMetadata.credentialConfigurationsSupported!!.keys, resolvedOffer.credentialConfigurationIds.first())

    // === create EC256 key and DID:JWK (did is not necessarily required, but currently needed for wallet initialization) ===
    val keysApi = KeysApi(client)
    lateinit var generatedKeyId: String
    lateinit var generatedDid: String
    keysApi.generate(UUID(walletId), KeyGenerationRequest(keyType = KeyType.secp256r1)) { generatedKeyId = it }
    DidsApi(client).create(UUID(walletId), DidsApi.DidCreateRequest("jwk", keyId = generatedKeyId)) { generatedDid = it }

    // === use credential offer request ===
    client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest?did=$generatedDid") {
      setBody(offerUri)
    }.expectSuccess()
  }
}
