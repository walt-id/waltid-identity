package id.walt.issuer2.openid4vci

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.extension.BasicConstraintsExtension.Companion.extensionBasicConstraints
import id.walt.certificate.x509.extension.KeyUsageExtension
import id.walt.certificate.x509.extension.KeyUsageExtension.Companion.extensionKeyUsage
import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.issuer2.application.Issuer2Module
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.config.TrustDecisionResolverConfig
import id.walt.issuer2.configurePlugins
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.repository.ConfiguredIssuanceSessionRepository
import id.walt.issuer2.testsupport.*
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.proofs.attestation.KeyAttestationConfig
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerificationMethod
import id.walt.trust.model.TrustedEntityType
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Key attestations trusted through `x509-trust-registry` while the trust resolver cannot be reached. */
class Issuer2AttestationTrustRegistryTest {
    // Started only to allow loopback in the fetch guard (and restore it afterwards); the resolver port stays closed.
    private val loopbackGuard = Issuer2TestNotificationServer()

    @AfterEach fun clear() {
        loopbackGuard.stopServer()
        clearIssuer2TestEnvironment()
    }

    @Test fun deferredOfferReportsUnavailableKeyAttestationTrustAsServerError() = verifyUnavailableResolver(deferred = true)

    @Test fun immediateOfferReportsUnavailableKeyAttestationTrustAsServerError() = verifyUnavailableResolver(deferred = false)

    private fun verifyUnavailableResolver(deferred: Boolean) = testApplication {
        loadIssuer2ConfigFiles()
        loopbackGuard.startServer()
        val closedPort = ServerSocket(0).use { it.localPort }
        val clientAttestation = createIssuer2ClientAttestationTestMaterial()
        val serviceConfig = ConfigManager.getConfig<Issuer2ServiceConfig>().copy(
            clientAuthenticationConfig = clientAttestation.clientAuthenticationConfig,
            keyAttestationConfig = KeyAttestationConfig(
                KeyAttestationVerificationMethod.X509TrustRegistry(setOf(TrustedEntityType.WALLET_PROVIDER)),
            ),
            attestationTrustResolver = TrustDecisionResolverConfig("http://127.0.0.1:$closedPort/resolve/certificate-chain"),
        )
        val metadataConfig = ConfigManager.getConfig<Issuer2MetadataConfig>().let { metadata ->
            metadata.copy(credentialConfigurations = metadata.credentialConfigurations.mapValues { (_, value) ->
                JsonObject(value.jsonObject + ("proof_types_supported" to buildJsonObject {
                    putJsonObject("jwt") {
                        put("proof_signing_alg_values_supported", JsonArray(listOf(JsonPrimitive("ES256"))))
                        putJsonObject("key_attestations_required") {}
                    }
                }))
            })
        }
        val repository = ConfiguredIssuanceSessionRepository()
        val module = Issuer2Module(serviceConfig, metadataConfig, ConfigManager.getConfig(), issuanceSessionRepository = repository)
        application {
            install(ContentNegotiation) { json(issuer2TestJson) }
            installIssuer2AuthenticationForTests()
            configurePlugins()
            routing { module.managementController.register(this); module.openId4VciController.register(this) }
        }
        val client = apiClient()
        val offer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_DEFERRED_OFFER.copy(
            profileId = "identityCredentialSdJwt",
            authMethod = AuthenticationMethod.PRE_AUTHORIZED,
            deferred = deferred,
        ))
        val resolved = Issuer2WalletFlowDriver(client).resolve(offer)
        val wia = clientAttestation.attestationAssembler.buildAttestationHeaders(
            instanceKey = JWKKey.generate(KeyType.secp256r1),
            clientId = "issuer2-wallet-test",
            audience = resolved.authorizationServerMetadata.issuer,
        )
        val token = client.submitForm(assertNotNull(resolved.authorizationServerMetadata.tokenEndpoint), Parameters.build {
            append("client_id", "issuer2-wallet-test")
            append("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
            append("pre-authorized_code", assertNotNull(resolved.offer.grants?.preAuthorizedCode?.preAuthorizedCode))
        }) {
            header(ClientAttestationHeaders.CLIENT_ATTESTATION, wia.attestationJwt)
            header(ClientAttestationHeaders.CLIENT_ATTESTATION_POP, wia.popJwt)
        }
        assertEquals(HttpStatusCode.OK, token.status, token.bodyAsText())
        val accessToken = token.body<JsonObject>().getValue("access_token").jsonPrimitive.content
        val nonce = client.post(assertNotNull(resolved.issuerMetadata.nonceEndpoint)).body<JsonObject>()
            .getValue("c_nonce").jsonPrimitive.content

        val holder = JWKKey.generate(KeyType.secp256r1)
        val now = Clock.System.now().epochSeconds
        val keyAttestation = registryKeyAttestation(holder.getPublicKey().exportJWKObject(), nonce, now)
        val proof = holder.signJws(buildJsonObject {
            put("iss", "issuer2-wallet-test"); put("aud", resolved.issuerMetadata.credentialIssuer)
            put("iat", now); put("nonce", nonce)
        }.toString().encodeToByteArray(), mapOf(
            "typ" to JsonPrimitive("openid4vci-proof+jwt"), "jwk" to holder.getPublicKey().exportJWKObject(),
            "key_attestation" to JsonPrimitive(keyAttestation),
        ))
        val response = client.post(resolved.issuerMetadata.credentialEndpoint) {
            bearerAuth(accessToken); contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("credential_configuration_id", resolved.offer.credentialConfigurationIds.single())
                putJsonObject("proofs") { put("jwt", JsonArray(listOf(JsonPrimitive(proof)))) }
            })
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status, response.bodyAsText())
        val error = response.body<JsonObject>()
        assertEquals("server_error", error.getValue("error").jsonPrimitive.content)
        // Deferred acceptance stops before the session is claimed; the immediate path fails the claimed request.
        assertEquals(
            if (deferred) "Credential proof verification unavailable" else "Key attestation trust source is unavailable",
            error.getValue("error_description").jsonPrimitive.content,
        )
        val session = assertNotNull(repository.get(offer.offerId))
        assertTrue(session.issuanceResults.isEmpty())
        if (deferred) assertTrue(assertNotNull(session.deferredState).transactions.isEmpty())
    }

    /** A key attestation signed by a wallet-provider leaf, carrying its leaf-first x5c chain. */
    private suspend fun registryKeyAttestation(attestedKey: JsonObject, nonce: String, now: Long): String {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        suspend fun p256(id: String) = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY)),
        )
        val signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val caKey = p256("wallet-provider-ca")
        val leafKey = p256("wallet-provider-key-attester")
        val ca = X509CertificateUtil.createSelfSignedCertificate(caKey, signatureAlgorithm) {
            subjectDn = "CN=Test Wallet Provider CA"
            extensionBasicConstraints { cA = true }
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.keyCertSign) }
        }
        val leaf = X509CertificateUtil.createCertificate(caKey, ca, signatureAlgorithm) {
            subjectDn = "CN=Test Key Attester"
            subjectPublicKey(leafKey)
            extensionKeyUsage { addKeyUsage(KeyUsageExtension.KeyUsage.digitalSignature) }
        }
        return CompactJws.sign(buildJsonObject {
            put("iat", now); put("exp", now + 300); put("nonce", nonce)
            put("attested_keys", JsonArray(listOf(attestedKey)))
        }.toString().encodeToByteArray(), leafKey, JwsAlgorithm.ES256, buildJsonObject {
            put("typ", "key-attestation+jwt")
            put("x5c", JsonArray(listOf(leaf, ca).map { JsonPrimitive(Base64.encode(it.encodedDer.toByteArray())) }))
        })
    }
}
