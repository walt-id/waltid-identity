package id.walt.wallet2.server.openapi

import id.walt.wallet2.handlers.*
import io.ktor.http.Url
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.wallet2.server.handlers.CreateDidRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object Wallet2RequestExamples {

    // Replace offer, key IDs and authorization code with values from your running issuer/wallet.
    val RECEIVE_SINGLE = ReceiveCredentialRequest(offerUrl = Url("https://issuer.example/offer"))
    val RECEIVE_BATCH = RECEIVE_SINGLE.copy(credentials = listOf(
        WalletCredentialSelection("identity_credential", holderBindings = listOf(
            CredentialHolderBinding(keyId = "holder-1"), CredentialHolderBinding(keyId = "holder-2"),
        )),
    ))
    val RECEIVE_MULTIPLE = RECEIVE_BATCH.copy(credentials = RECEIVE_BATCH.credentials!! +
        WalletCredentialSelection("org.iso.23220.photoid.1", holderBindings = listOf(CredentialHolderBinding("holder-3"))))
    val AUTHORIZE_WITHOUT_OFFER = GenerateAuthorizationUrlRequest(
        credentialIssuer = "https://issuer.example",
        credentialConfigurationIds = listOf("identity_credential", "org.iso.23220.photoid.1"),
        redirectUri = Url("openid://callback"),
    )
    val REQUEST_TOKEN_AUTOMATIC = RequestTokenRequest(
        tokenEndpoint = Url("https://issuer.example/token"),
        preAuthorizedCode = "code-from-resolved-offer",
        credentialIssuer = "https://issuer.example",
        credentialConfigurationIds = listOf("identity_credential", "org.iso.23220.photoid.1"),
    )
    val RECEIVE_AUTHORIZED_BATCH = ReceiveAuthorizedCredentialRequest(
        credentials = RECEIVE_BATCH.credentials!!,
        code = "code-from-browser-callback", codeVerifier = "verifier-from-authorization-url",
        credentialIssuer = "https://issuer.example", credentialEndpoint = Url("https://issuer.example/credential"),
        redirectUri = Url("openid://callback"),
    )
    const val BATCH_DESCRIPTION = "One instance per selected configuration is the default. " +
        "For an explicit batch, supply one holderBindings entry per instance and stay within the preview batchSize. " +
        "Multiple configurations/datasets use separate requests under the same token. " +
        "Authorization details or scopes are selected automatically from issuer metadata. " +
        "Token authorization_details are expanded into credential_identifier requests automatically. " +
        "Use existing wallet keys; issuance never generates keys implicitly."


    private val EXAMPLE_STATIC_KEY: JsonObject = buildJsonObject {
        put("type", "jwk")
        putJsonObject("jwk") {
            put("kty", "OKP")
            put("crv", "Ed25519")
            put("x", "11qYAYSMxJGKNDAUzHfDQoHZMdBgvPG3Umcatb14L3Rz")
            put("d", "n4t_0SPOkIarCSMn9-eMq0InSnBXFA9773BkoXHiLimhM")
        }
    }

    val CREATE_WALLET_DEFAULT = CreateWalletRequest()

    val CREATE_WALLET_INLINE_NO_STORES = CreateWalletRequest(
        keyStoreIds = emptyList(),
        credentialStoreIds = emptyList(),
        noDidStore = true,
        staticKey = EXAMPLE_STATIC_KEY,
        staticDid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbhnRFvvWUpK",
    )

    val CREATE_WALLET_SINGLE_NAMED_STORE_EACH = CreateWalletRequest(
        keyStoreIds = listOf("main-kms"),
        credentialStoreIds = listOf("main-credentials"),
        didStoreId = "main-dids",
    )

    val CREATE_WALLET_MULTIPLE_NAMED_STORES = CreateWalletRequest(
        keyStoreIds = listOf("kms-primary", "kms-backup"),
        credentialStoreIds = listOf("personal-credentials", "work-credentials"),
        didStoreId = "shared-dids",
    )

    val GENERATE_KEY_ED25519 = TypedKeyGenerationRequest.Jwk(keyType = KeyType.Ed25519)

    val GENERATE_KEY_SECP256R1 = TypedKeyGenerationRequest.Jwk(keyType = KeyType.secp256r1)

    val GENERATE_KEY_SECP256K1 = TypedKeyGenerationRequest.Jwk(keyType = KeyType.secp256k1)

    val CREATE_DID_KEY_WITH_KEY_ID = CreateDidRequest(
        method = "key",
        keyId = "v_CW0xEd25519ExampleKeyId",
    )

    val CREATE_DID_KEY_DEFAULT_KEY = CreateDidRequest(
        method = "key",
    )

    val CREATE_DID_JWK_WITH_KEY_ID = CreateDidRequest(
        method = "jwk",
        keyId = "v_CW0xP256ExampleKeyId",
    )

    val CREATE_DID_WEB_WITH_DOMAIN_AND_PATH = CreateDidRequest(
        method = "web",
        keyId = "v_CW0xEd25519ExampleKeyId",
        options = mapOf(
            "domain" to JsonPrimitive("example.com"),
            "path" to JsonPrimitive("/users/alice"),
        ),
    )
}
