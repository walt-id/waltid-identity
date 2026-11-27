package id.walt.wallet2.mobile

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.Crypto2DidService
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.keys.MobileWalletKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryIssuanceSessionStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Shared Android and native Swift test peer. Reopening uses the same wallet stores. */
public class SigningRecoveryIssuer {
    public var exchanges: Int = 0
        private set
    public var prompts: Int = 0
        private set
    public var signatures: Int = 0
        private set
    public var deliveries: Int = 0
        private set
    public val offer: String = "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer"
    private val issuer = "https://issuer.example"
    private val records = InMemoryIssuanceSessionStore()
    private val keys = InMemoryKeyStore()
    private val credentials = InMemoryCredentialStore()
    private var holderDid: String? = null

    private fun client() = HttpClient(MockEngine) {
        engine { addHandler { request ->
            val body = when (request.url.encodedPath) {
                "/offer" -> """{"credential_issuer":"$issuer","credential_configuration_ids":["identity"],"grants":{"urn:ietf:params:oauth:grant-type:pre-authorized_code":{"pre-authorized_code":"once"}}}"""
                "/.well-known/openid-credential-issuer" -> """{"credential_issuer":"$issuer","credential_endpoint":"$issuer/credential","credential_configurations_supported":{"identity":{"format":"jwt_vc_json","credential_definition":{"type":["VerifiableCredential","TestCredential"]},"cryptographic_binding_methods_supported":["jwk"],"proof_types_supported":{"jwt":{"proof_signing_alg_values_supported":["ES256"]}}}}}"""
                "/.well-known/oauth-authorization-server" -> """{"issuer":"$issuer","token_endpoint":"$issuer/token","grant_types_supported":["urn:ietf:params:oauth:grant-type:pre-authorized_code"],"pre-authorized_grant_anonymous_access_supported":true}"""
                "/token" -> {
                    check(++exchanges == 1)
                    """{"access_token":"exchanged","token_type":"Bearer","expires_in":300}"""
                }
                "/credential" -> {
                    check(++deliveries == 1)
                    val subject = checkNotNull(holderDid)
                    val payload = """{"iss":"$issuer","sub":"$subject","vc":{"@context":["https://www.w3.org/2018/credentials/v1"],"type":["VerifiableCredential","TestCredential"],"credentialSubject":{"id":"$subject"}}}"""
                    val issuerKey = generateKey("issuer")
                    val credential = CompactJws.sign(payload.encodeToByteArray(), issuerKey, JwsAlgorithm.ES256)
                    """{"credentials":[{"credential":"$credential"}]}"""
                }
                else -> error("Unexpected issuer request")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        } }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private suspend fun generateKey(id: String): Key =
        CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY))
        )

    public suspend fun openWallet(): MobileWallet {
        if (holderDid == null) {
            val holder = generateKey("holder")
            MobileDidSupport.ensureInitialized()
            holderDid = Crypto2DidService.registerByKey("key", holder).did
            keys.addCrypto2Key(object : Key by holder {
                override val capabilities = holder.capabilities.copy(signer = Signer { data, algorithm ->
                    if (++prompts == 1) throw KeyUseAuthorizationException(
                        KeyUseAuthorizationFailure.AuthorizationNotCompleted, "Holder dismissed signing")
                    requireNotNull(holder.capabilities.signer).sign(data, algorithm).also { signatures++ }
                })
            })
        }
        val protectedKeys = object : MobileWalletKeyStore, WalletKeyStore by keys {
            override suspend fun keyUseAuthorizationPolicy(keyId: String): KeyUseAuthorizationPolicy =
                KeyUseAuthorizationPolicy.BiometricCurrentSet
        }
        return MobileWallet("signing-recovery", keyStore = protectedKeys, credentialStore = credentials,
            didStore = InMemoryDidStore(), issuanceSessionStore = records, issuanceHttpClient = client())
    }
}
