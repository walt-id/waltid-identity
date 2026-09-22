package id.walt.wallet2.mobile

import id.walt.credentials.CredentialParser
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.sdjwt.*
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.persistence.keys.*
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import kotlinx.serialization.json.*

/** Synthetic issuer and payment data, shared by host and opt-in native tests. */
internal data class ScaWalletFixture(val wallet: MobileWallet, val request: JsonObject) {
    suspend fun preview(): MobileWalletDigitalCredentialPreview = wallet.previewDigitalCredentialPresentation(
        MobileWalletDigitalCredentialRequest(
            protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_UNSIGNED,
            dataJson = request.toString(), verifiedOrigin = "https://verifier.example",
        ),
    )
    suspend fun submit(preview: MobileWalletDigitalCredentialPreview): MobileWalletDigitalCredentialResponse =
        wallet.submitDigitalCredentialPresentation(preview.requestId, preview.credentialOptions.map {
            MobileWalletPresentationCredentialSelection(it.queryId, it.credentialId)
        })
}

internal suspend fun scaWalletFixture(key: Key, provider: PlatformManagedKeyProvider): ScaWalletFixture {
    val issuer = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
        GenerateSoftwareKeyRequest(KeyId("synthetic-issuer"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
    )
    val vct = "https://webuildconsortium.eu/sca/sca-iban/1.0"
    val payload = SDPayload.createSDPayload(buildJsonObject {
        put("iss", "https://issuer.example"); put("vct", vct)
        put("cnf", buildJsonObject {
            put("jwk", Json.parseToJsonElement(requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(key.spec).data.toByteArray().decodeToString()))
        })
        put("masked_iban", "DE**1234")
    }, SDMap(mapOf("masked_iban" to SDField(sd = true))))
    val signer = Crypto2AsyncJWTCryptoProvider(mapOf("issuer" to Crypto2SdJwtKey(issuer, JwsAlgorithm.ES256)))
    val credential = SDJwt.createFromSignedJwt(signer.sign(payload.undisclosedPayload, "issuer", "dc+sd-jwt", emptyMap()), payload)
    val transaction = """{"type":"urn:eudi:sca:payment:1","credential_ids":["payment"],"transaction_data_hashes_alg":["sha-256"],"payload":{"transaction_id":"synthetic","amount":12.5,"currency":"EUR","payee":{"name":"Synthetic shop","id":"synthetic"}}}"""
        .encodeToByteArray().encodeToBase64Url()
    val request = buildJsonObject {
        put("response_type", "vp_token"); put("response_mode", "dc_api"); put("nonce", "synthetic-nonce")
        put("transaction_data", buildJsonArray { add(transaction) })
        put("dcql_query", buildJsonObject { put("credentials", buildJsonArray {
            add(buildJsonObject {
                put("id", "payment"); put("format", "dc+sd-jwt")
                put("meta", buildJsonObject { put("vct_values", buildJsonArray { add(vct) }) })
                put("claims", buildJsonArray { add(buildJsonObject { put("path", buildJsonArray { add("masked_iban") }) }) })
            })
        }) })
    }
    val backingStore = InMemoryKeyStore().also { it.addCrypto2Key(key) }
    val keys = object : MobileWalletKeyStore, WalletKeyStore by backingStore {
        override suspend fun keyUseAuthorizationPolicy(keyId: String): KeyUseAuthorizationPolicy? =
            (backingStore.getKeyMaterial(keyId)?.crypto2Key as? ManagedKey)?.let {
                provider.keyUseAuthorizationPolicy(it.storedKey)
            }
    }
    val credentials = InMemoryCredentialStore().also {
        it.addCredential(StoredCredential("synthetic-sca", CredentialParser.detectAndParse(credential.toString()).second))
    }
    val wallet = MobileWallet(
        walletId = "synthetic-sca-wallet", keyStore = keys, didStore = InMemoryDidStore(), credentialStore = credentials,
        transactionDataProfiles = listOf(MobileWalletTransactionDataProfile("urn:eudi:sca:payment:1")),
        scaAuthorizer = NativeScaPresentationAuthorizer(provider),
    )
    return ScaWalletFixture(wallet, request)
}
