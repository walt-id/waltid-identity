package id.walt.wallet2.server.openapi

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
