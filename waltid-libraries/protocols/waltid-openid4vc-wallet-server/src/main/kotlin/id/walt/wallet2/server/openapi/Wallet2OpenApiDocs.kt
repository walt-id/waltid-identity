package id.walt.wallet2.server.openapi

import id.walt.crypto.keys.TypedKeyGenerationRequest
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.server.handlers.CreateDidRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Wallet2OpenApiDocs {

    fun createWallet(): RouteConfig.() -> Unit = {
        summary = "Create a new wallet"
        description = """
            Creates a wallet with optional named stores or inline key/DID material.
            Omitting storeIDs auto-creates one in-memory store of each type.
        """.trimIndent()
        request {
            body<CreateWalletRequest> {
                required = false
                example("Default — auto-create one store of each type") {
                    value = Wallet2RequestExamples.CREATE_WALLET_DEFAULT
                }
                example("Inline key and DID — no attached stores") {
                    value = Wallet2RequestExamples.CREATE_WALLET_INLINE_NO_STORES
                }
                example("Single named store of each type") {
                    value = Wallet2RequestExamples.CREATE_WALLET_SINGLE_NAMED_STORE_EACH
                }
                example("Multiple key and credential stores, single shared DID store") {
                    value = Wallet2RequestExamples.CREATE_WALLET_MULTIPLE_NAMED_STORES
                }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "Wallet created"
                body<WalletCreatedResponse> {
                    example("Created wallet ID") {
                        value = WalletCreatedResponse(walletId = "9bbbc42d-8f3a-4b1c-9e2d-1a2b3c4d5e6f")
                    }
                }
            }
        }
    }

    fun generateKey(): RouteConfig.() -> Unit = {
        summary = "Generate a new key"
        description = """
            Generates a key in the wallet's first attached key store and returns its ID.

            Supported `keyType` values: `Ed25519` (default), `secp256r1` (NIST P-256),
            `secp256k1`, `secp384r1`, `secp521r1`, `RSA`, `RSA3072`, `RSA4096`.

            Wallets with only a `staticKey` and no key stores will not be able to generate keys.
        """.trimIndent()
        request {
            pathParameter<String>("walletId") {
                description = "Wallet ID returned by POST /wallet"
                example("Wallet ID") { value = "9bbbc42d-8f3a-4b1c-9e2d-1a2b3c4d5e6f" }
            }
            body<TypedKeyGenerationRequest> {
                example("Ed25519 (JWK)") {
                    value = Wallet2RequestExamples.GENERATE_KEY_ED25519
                }
                example("secp256r1 / NIST P-256 (JWK)") {
                    value = Wallet2RequestExamples.GENERATE_KEY_SECP256R1
                }
                example("secp256k1 (JWK)") {
                    value = Wallet2RequestExamples.GENERATE_KEY_SECP256K1
                }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "Key generated"
                body<WalletKeyInfo> {
                    example("Ed25519 key") {
                        value = WalletKeyInfo(keyId = "v_CW0xEd25519ExampleKeyId", keyType = "Ed25519")
                    }
                    example("secp256r1 key") {
                        value = WalletKeyInfo(keyId = "v_CW0xP256ExampleKeyId", keyType = "secp256r1")
                    }
                }
            }
        }
    }

    fun createDid(): RouteConfig.() -> Unit = {
        summary = "Create a DID"
        description = """
            Registers a DID in the wallet's DID store using an existing key.

            Common `method` values: `key` (did:key) and `jwk` (did:jwk). When `keyId` is omitted,
            the wallet's default key is used — generate one first via
            `POST /wallet/{walletId}/keys/generate` if the wallet has no keys yet.

            Wallets without a DID store (inline `staticDid` or `noDidStore`) will not be able to create DIDs.
        """.trimIndent()
        request {
            pathParameter<String>("walletId") {
                description = "Wallet ID returned by POST /wallet"
                example("Wallet ID") { value = "9bbbc42d-8f3a-4b1c-9e2d-1a2b3c4d5e6f" }
            }
            body<CreateDidRequest> {
                example("did:key bound to a specific key") {
                    value = Wallet2RequestExamples.CREATE_DID_KEY_WITH_KEY_ID
                }
                example("did:key using the wallet default key") {
                    value = Wallet2RequestExamples.CREATE_DID_KEY_DEFAULT_KEY
                }
                example("did:jwk bound to a specific key") {
                    value = Wallet2RequestExamples.CREATE_DID_JWK_WITH_KEY_ID
                }
            }
        }
        response {
            HttpStatusCode.Created to {
                description = "DID created"
                body<WalletDidEntry> {
                    example("did:key entry") {
                        value = WalletDidEntry(
                            did = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbhnRFvvWUpK",
                            document = buildJsonObject {
                                put("@context", "https://www.w3.org/ns/did/v1")
                                put("id", "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbhnRFvvWUpK")
                            },
                        )
                    }
                }
            }
        }
    }
}
