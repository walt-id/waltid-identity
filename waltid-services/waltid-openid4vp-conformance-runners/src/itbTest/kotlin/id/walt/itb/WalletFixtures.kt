package id.walt.itb

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import id.walt.openid4vp.conformance.wallet.WalletCredentialIssuer
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.*
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.Url
import kotlinx.serialization.json.*

/** Synthetic credentials and fresh holder keys; no ITB credentials or captured personal data. */
internal class WalletFixtures {
    val issuer = WalletCredentialIssuer()
    val json = Json { ignoreUnknownKeys = true }

    suspend fun wallet(): Wallet = Wallet(
        id = "itb-wallet",
        keyStores = listOf(InMemoryKeyStore().apply { addCrypto2Key(issuer.holderCrypto2Key()) }),
        credentialStores = listOf(InMemoryCredentialStore()),
    )

    fun credential(vct: String = IDENTITY_VCT): String = SDJwtVC.sign(
        sdPayload = SDPayload.createSDPayload(
            fullPayload = buildJsonObject { put("family_name", "Example"); put("iban", "DE02120300000000202051") },
            disclosureMap = SDMap.generateSDMap(setOf("family_name", "iban")),
        ),
        jwtCryptoProvider = SimpleJWTCryptoProvider(
            jwsAlgorithm = JWSAlgorithm.ES256,
            jwsSigner = ECDSASigner(ECKey.parse(TestKeyMaterial.CREDENTIAL_ISSUER_KEY_WITH_X5C)),
            jwsVerifier = null,
        ),
        issuerDid = ISSUER,
        holderKeyJWK = json.parseToJsonElement(issuer.holderKey.toPublicJWK().toJSONString()).jsonObject,
        issuerKeyId = "itb-fixture-issuer",
        vct = vct,
    ).toString()

    fun request(vct: String = IDENTITY_VCT, transactionData: List<String> = emptyList()): AuthorizationRequest =
        json.decodeFromJsonElement(buildJsonObject {
            put("client_id", CLIENT_ID)
            put("nonce", "itb-presentation-nonce")
            put("response_type", "vp_token")
            put("response_mode", "direct_post")
            put("response_uri", "$VERIFIER/response")
            put("dcql_query", query(vct))
            if (transactionData.isNotEmpty()) put("transaction_data", JsonArray(transactionData.map(::JsonPrimitive)))
        })

    /** Tests the real store selection, presentation and holder signing path, without HTTP transport. */
    suspend fun present(vct: String = IDENTITY_VCT, transactions: List<String> = emptyList()): JsonObject {
        val wallet = wallet()
        val stored = WalletCredentialHandler.importCredential(wallet, ImportCredentialRequest(credential(vct)))
        val request = request(vct, transactions)
        val result = WalletPresentationHandler.buildVpToken(
            wallet = wallet,
            request = BuildVpTokenRequest(
                requestUrl = Url("$VERIFIER/request"),
                selectedCredentialIds = mapOf("credential" to listOf(stored.id)),
            ),
            transactionDataTypeRegistry = TransactionDataTypeRegistry(PAYMENT_TYPE),
            resolveAuthorizationRequest = { ResolvedAuthorizationRequest.Plain(request) },
        )
        val presentation = json.parseToJsonElement(result.vpToken).jsonObject
            .getValue("credential").jsonArray.single().jsonPrimitive.content
        val kbJwt = presentation.substringAfterLast('~')
        val verified = CompactJws.verify(kbJwt, issuer.holderCrypto2Key(), JwsAlgorithm.ES256)
        return json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
    }

    /** Reviewed submission with explicitly simulated factors; never used by the live runner. */
    suspend fun presentPayment(authorizer: WalletScaPresentationAuthorizer?): JsonObject {
        val wallet = wallet()
        WalletCredentialHandler.importCredential(wallet, ImportCredentialRequest(credential()))
        val data = JsonObject(json.encodeToJsonElement(request(transactionData = listOf(payment()))).jsonObject
            .minus("client_id").minus("response_uri") + ("response_mode" to JsonPrimitive("dc_api")))
        val preview = WalletPresentationHandler.previewDcApiPresentation(
            wallet, PreviewDcApiPresentationRequest("openid4vp-v1-unsigned", data, VERIFIER),
            transactionDataTypeRegistry = TransactionDataTypeRegistry(PAYMENT_TYPE),
        )
        val result = WalletPresentationHandler.submitDcApiPresentation(
            wallet, SubmitDcApiPresentationRequest(preview.requestId, preview.credentialOptions.map {
                PresentationCredentialSelection(it.queryId, it.credentialId)
            }),
            transactionDataTypeRegistry = TransactionDataTypeRegistry(PAYMENT_TYPE),
            scaAuthorizer = authorizer,
        )
        val presentation = result.data.getValue("vp_token").jsonObject
            .getValue("credential").jsonArray.single().jsonPrimitive.content
        val verified = CompactJws.verify(presentation.substringAfterLast('~'), issuer.holderCrypto2Key(), JwsAlgorithm.ES256)
        return json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
    }

    companion object {
        const val ISSUER = "https://credentials.example.com"
        const val VERIFIER = "https://verifier.example.com"
        const val CLIENT_ID = "x509_san_dns:verifier.example.com"
        const val IDENTITY_VCT = "https://credentials.example.com/identity_credential"
        const val PAYMENT_TYPE = "urn:eudi:sca:payment:1"

        fun query(vct: String = IDENTITY_VCT): JsonObject = Json.parseToJsonElement(
            """{"credentials":[{"id":"credential","format":"dc+sd-jwt","meta":{"vct_values":["$vct"]}}]}"""
        ).jsonObject

        // Hash exactly this encoded string, never a decoded/re-serialized JSON representation.
        fun payment(): String = """{"type":"$PAYMENT_TYPE","credential_ids":["credential"],"transaction_data_hashes_alg":["sha-256"],"payload":{"transaction_id":"itb-synthetic-payment","payee":{"name":"Example merchant","id":"example-payee"},"currency":"EUR","amount":12.95}}"""
            .encodeToByteArray().encodeToBase64Url()

    }
}
