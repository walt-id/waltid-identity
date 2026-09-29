package id.walt.wallet2.handlers

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.resolveKeyMaterial
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.assertEquals
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.data.WalletKeyStoreEntry
import kotlinx.serialization.json.*

internal suspend fun batchTestCredential(publicJwk: JsonObject, vct: String = "identity"): String {
    val issuerKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId("test-issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN)),
    )
    return CompactJws.sign(
        key = issuerKey,
        algorithm = JwsAlgorithm.ES256,
        payload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("vct", vct)
            put("iat", 1700000000)
            put("given_name", "Ada")
            putJsonObject("cnf") { put("jwk", publicJwk) }
        }.toString().encodeToByteArray(),
        protectedHeader = buildJsonObject { put("typ", "dc+sd-jwt") },
    ) + "~"
}

internal suspend fun batchTestCredential(key: Key): String = batchTestCredential(key.getPublicKey().exportJWKObject())

internal suspend fun batchTestCredential(key: WalletKeyStoreEntry): String = batchTestCredential(
    key.crypto2Key?.let {
        val encoded = it.capabilities.publicKeyExporter!!.exportPublicKey().toPublicJwk(it.spec)
        Json.parseToJsonElement(encoded.data.toByteArray().decodeToString()).jsonObject
    } ?: key.legacyKey!!.getPublicKey().exportJWKObject()
)

internal suspend fun batchTestResponse(proofs: List<String>, vct: String = "identity"): String = buildJsonObject {
    put("credentials", JsonArray(proofs.map {
        val jwk = CompactJws.decodeUnverified(it).protectedHeader["jwk"]!!.jsonObject
        buildJsonObject { put("credential", batchTestCredential(jwk, vct)) }
    }))
}.toString()

internal const val BATCH_TEST_ISSUER = "https://issuer.example"
internal const val BATCH_TEST_TOKEN = """{"access_token":"access","token_type":"Bearer"}"""

internal suspend fun batchTestFixture(crypto2: Boolean): BatchTestFixture {
    val keys = InMemoryKeyStore()
    val ids = (1..5).map { index ->
        if (crypto2) keys.addCrypto2Key(CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId("holder-$index"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))))
        else keys.addKey(JWKKey.generate(KeyType.Ed25519))
    }
    val store = InMemoryCredentialStore()
    val wallet = Wallet("batch-wallet", keyStores = listOf(keys), credentialStores = listOf(store), defaultKeyId = ids.first())
    return BatchTestFixture(wallet, store, ids.map { wallet.resolveKeyMaterial(it, setOf(KeyUsage.SIGN))!! })
}

internal data class BatchTestFixture(val wallet: Wallet, val store: InMemoryCredentialStore, val keys: List<WalletKeyStoreEntry>) {
    fun selection(count: Int) = WalletCredentialSelection("identity", holderBindings = keys.take(count).map { CredentialHolderBinding(it.keyId) })
}

internal fun JsonObject.batchProofs(): List<String> = getValue("proofs").jsonObject.getValue("jwt").jsonArray.map { it.jsonPrimitive.content }
internal fun batchTestOffer(): JsonObject = Json.parseToJsonElement(
    """{"credential_issuer":"$BATCH_TEST_ISSUER","credential_configuration_ids":["identity"],
        "grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"pre-code"}}}""").jsonObject

internal fun batchTestMetadata(batchSize: Int = 5) = """{"credential_issuer":"$BATCH_TEST_ISSUER","credential_endpoint":"$BATCH_TEST_ISSUER/credential",
    "deferred_credential_endpoint":"$BATCH_TEST_ISSUER/deferred","nonce_endpoint":"$BATCH_TEST_ISSUER/nonce","batch_credential_issuance":{"batch_size":$batchSize},
    "credential_configurations_supported":{"identity":{"format":"dc+sd-jwt","vct":"identity","scope":"identity",
    "cryptographic_binding_methods_supported":["jwk"],"proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256","EdDSA"]}}}}}"""

internal fun batchTestClient(
    metadata: String = batchTestMetadata(),
    authorizationDetailsSupported: Boolean = true,
    token: (Parameters) -> String = { BATCH_TEST_TOKEN },
    par: ((Parameters) -> Unit)? = null,
    credentialStatus: HttpStatusCode = HttpStatusCode.OK,
    credential: suspend (JsonObject) -> String,
    deferredStatus: () -> HttpStatusCode = { HttpStatusCode.OK },
    deferred: suspend (JsonObject) -> String = { error("Unexpected deferred request") },
) = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            var status = HttpStatusCode.OK
            val content = when (request.url.toString()) {
                "$BATCH_TEST_ISSUER/.well-known/openid-credential-issuer" -> metadata
                "$BATCH_TEST_ISSUER/.well-known/oauth-authorization-server" ->
                    """{"issuer":"$BATCH_TEST_ISSUER","token_endpoint":"$BATCH_TEST_ISSUER/token","authorization_endpoint":"$BATCH_TEST_ISSUER/authorize",
                        ${if (authorizationDetailsSupported) "\"authorization_details_types_supported\":[\"openid_credential\"]," else ""}
                        ${if (par != null) "\"pushed_authorization_request_endpoint\":\"$BATCH_TEST_ISSUER/par\",\"require_pushed_authorization_requests\":true," else ""}
                        "response_types_supported":["code"]}"""
                "$BATCH_TEST_ISSUER/token" -> token(parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
                "$BATCH_TEST_ISSUER/par" -> {
                    status = HttpStatusCode.Created
                    requireNotNull(par)(parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
                    """{"request_uri":"urn:ietf:params:oauth:request_uri:test","expires_in":60}"""
                }
                "$BATCH_TEST_ISSUER/nonce" -> """{"c_nonce":"nonce"}"""
                "$BATCH_TEST_ISSUER/credential" -> {
                    assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
                    status = credentialStatus
                    credential(Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject)
                }
                "$BATCH_TEST_ISSUER/deferred" -> {
                    status = deferredStatus()
                    deferred(Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject)
                }
                else -> error("Unexpected endpoint ${request.url}")
            }
            respond(content, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
    }
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}
