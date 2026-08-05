@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.credentials.keyresolver.Crypto2JwtKeyResolver
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.Jwk
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.openid4vp.clientidprefix.extractSanDnsNamesFromDer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import id.walt.x509.validateClientAuthenticationCertificateUsage
import io.ktor.http.Url
import kotlin.time.Clock

/**
 * Handles `verifier_attestation` prefix per OID4VP 1.0, Section 5.9.3 and §12.
 *
 * Validation steps:
 * 1. Extract the Verifier Attestation JWT from the `jwt` JOSE header of the signed request.
 * 2. Resolve the attestation issuer's public key (via DID, x5c, or HTTPS well-known) and verify its signature.
 * 3. Validate that `sub` in the attestation equals the `client_id`.
 * 4. Extract the verifier's public key from `cnf.jwk` in the attestation.
 * 5. Verify the main request JWS signature with that key (proof of possession).
 */
@Serializable
data class VerifierAttestation(val sub: String, override val rawValue: String) : ClientId {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val EXPECTED_TYP = "verifier-attestation+jwt"
    }

    suspend fun authenticateVerifierAttestation(
        clientId: VerifierAttestation,
        context: RequestContext,
    ): ClientValidationResult = authenticateVerifierAttestation(clientId, context, ClientIdTrustConfiguration())

    suspend fun authenticateVerifierAttestation(
        clientId: VerifierAttestation,
        context: RequestContext,
        trustConfiguration: ClientIdTrustConfiguration,
    ): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        return try {
            val decodedRequest = CompactJws.decodeUnverified(jws)

            // 1. Extract the Verifier Attestation JWT from the `jwt` JOSE header
            val attestationJwtString = decodedRequest.protectedHeader["jwt"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException(
                    "Signed request is missing the 'jwt' JOSE header required for verifier_attestation"
                )

            val decodedAttestation = CompactJws.decodeUnverified(attestationJwtString)
            val attestationPayload = Json.parseToJsonElement(
                decodedAttestation.payload.decodeToString(throwOnInvalidSequence = true)
            ) as? JsonObject ?: throw IllegalArgumentException("Verifier Attestation payload must be a JSON object")
            val attestationIssuer = attestationPayload["iss"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("Verifier Attestation JWT is missing 'iss' claim")
            require(attestationIssuer in trustConfiguration.trustedVerifierAttestationIssuers) {
                "Verifier Attestation issuer is not trusted: $attestationIssuer"
            }
            val now = Clock.System.now().epochSeconds
            val expiresAtPrimitive = attestationPayload["exp"]?.jsonPrimitive
                ?: throw IllegalArgumentException("Verifier Attestation JWT is missing 'exp' claim")
            require(!expiresAtPrimitive.isString) { "Verifier Attestation exp claim must be numeric" }
            val expiresAt = expiresAtPrimitive.longOrNull
                ?: throw IllegalArgumentException("Verifier Attestation exp claim must be numeric")
            require(expiresAt > now) { "Verifier Attestation JWT is expired" }
            attestationPayload["nbf"]?.let { notBeforeElement ->
                val notBeforePrimitive = notBeforeElement.jsonPrimitive
                require(!notBeforePrimitive.isString) { "Verifier Attestation nbf claim must be numeric" }
                val notBefore = notBeforePrimitive.longOrNull
                    ?: throw IllegalArgumentException("Verifier Attestation nbf claim must be numeric")
                require(notBefore <= now) { "Verifier Attestation JWT is not yet valid" }
            }
            (context.responseUri ?: context.redirectUri)?.let { responseUri ->
                attestationPayload["redirect_uris"]?.let { redirectUrisElement ->
                    val redirectUris = redirectUrisElement.jsonArray.map { it.jsonPrimitive.content }
                    require(responseUri in redirectUris) { "Response URI is not authorized by the Verifier Attestation" }
                }
            }

            // Validate typ header per OID4VP §12
            val typ = decodedAttestation.protectedHeader["typ"]?.jsonPrimitive?.contentOrNull
            if (typ != EXPECTED_TYP) {
                throw IllegalArgumentException(
                    "Verifier Attestation JWT has incorrect 'typ': expected '$EXPECTED_TYP', got '$typ'"
                )
            }

            // 2. Resolve the attestation issuer's public key and verify the attestation JWT signature
            val x5c = decodedAttestation.protectedHeader["x5c"]?.jsonArray
            if (x5c != null) {
                require(trustConfiguration.x509TrustAnchors.isNotEmpty()) {
                    "Verifier Attestation x5c requires configured trust anchors"
                }
                val certificates = x5c.map { CertificateDer(it.jsonPrimitive.content.decodeFromBase64()) }
                val leaf = certificates.firstOrNull()
                    ?: throw IllegalArgumentException("Verifier Attestation x5c cannot be empty")
                validateCertificateChain(
                    leaf = leaf,
                    chain = certificates.drop(1),
                    trustAnchors = trustConfiguration.x509TrustAnchors,
                )
                leaf.validateClientAuthenticationCertificateUsage()
                require(attestationIssuer.startsWith("https://")) {
                    "Verifier Attestation x5c issuer must be an HTTPS URI"
                }
                val issuerHost = Url(attestationIssuer).host
                val issuerDnsNames = extractSanDnsNamesFromDer(leaf.bytes.toByteArray()).getOrThrow()
                require(issuerHost in issuerDnsNames) {
                    "Verifier Attestation certificate SAN does not match issuer host"
                }
                if (decodedAttestation.algorithm == JwsAlgorithm.ES256K) {
                    JWKKey.importFromDerCertificate(leaf.bytes.toByteArray()).getOrThrow()
                        .verifyJws(attestationJwtString).getOrThrow()
                } else {
                    ClientIdCrypto2.verify(attestationJwtString, ClientIdCrypto2.keyFromCertificate(leaf.bytes.toByteArray()))
                }
            } else if (decodedAttestation.algorithm == JwsAlgorithm.ES256K) {
                requireNotNull(decodedAttestation.protectedHeader["kid"]?.jsonPrimitive?.contentOrNull) {
                    "ES256K Verifier Attestation must include kid"
                }
                val attestationIssuerKey = JwtKeyResolver.resolveFromJwt(
                    jwtHeader = JsonObject(decodedAttestation.protectedHeader - "jwk" - "x5c"),
                    jwtPayload = attestationPayload,
                ) ?: throw IllegalArgumentException("Could not resolve ES256K Verifier Attestation issuer key")
                val attestationKid = requireNotNull(decodedAttestation.protectedHeader["kid"]?.jsonPrimitive?.contentOrNull)
                require(attestationIssuerKey.getKeyId() == attestationKid) {
                    "Resolved ES256K Verifier Attestation key does not exactly match kid"
                }
                attestationIssuerKey.exportJWKObject()["alg"]?.jsonPrimitive?.contentOrNull?.let { declaredAlgorithm ->
                    require(declaredAlgorithm == JwsAlgorithm.ES256K.identifier) {
                        "Verifier Attestation issuer JWK alg does not permit ES256K"
                    }
                }
                attestationIssuerKey.verifyJws(attestationJwtString).getOrThrow()
            } else {
                val attestationIssuerKey = Crypto2JwtKeyResolver().resolveFromJwt(
                    jwtHeader = JsonObject(decodedAttestation.protectedHeader - "jwk" - "x5c"),
                    jwtPayload = attestationPayload,
                )?.key ?: throw IllegalArgumentException(
                    "Could not resolve public key for Verifier Attestation issuer " +
                        "(iss=${attestationPayload["iss"]?.jsonPrimitive?.contentOrNull})"
                )
                ClientIdCrypto2.verify(attestationJwtString, attestationIssuerKey)
            }

            // 3. Validate that `sub` matches the client_id
            val attestationSub = attestationPayload["sub"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("Verifier Attestation JWT is missing 'sub' claim")

            if (attestationSub != clientId.sub) {
                throw IllegalArgumentException(
                    "Verifier Attestation 'sub' ($attestationSub) does not match client_id (${clientId.sub})"
                )
            }

            // 4. Extract verifier's public key from cnf.jwk
            val cnf = attestationPayload["cnf"]?.jsonObject
                ?: throw IllegalArgumentException("Verifier Attestation JWT is missing 'cnf' claim")
            val cnfJwk = cnf["jwk"]?.jsonObject
                ?: throw IllegalArgumentException("Verifier Attestation JWT 'cnf' is missing 'jwk' member")
            require(!Jwk.containsPrivateMaterial(cnfJwk)) { "Verifier Attestation cnf.jwk must be public" }
            cnfJwk["alg"]?.jsonPrimitive?.contentOrNull?.let { declaredAlgorithm ->
                require(declaredAlgorithm == decodedRequest.algorithm.identifier) {
                    "Verifier Attestation cnf.jwk alg does not permit request algorithm"
                }
            }

            // 5. Verify the main request JWS with the verifier's key (proof of possession)
            if (decodedRequest.algorithm == JwsAlgorithm.ES256K) {
                JWKKey.importJWK(cnfJwk.toString()).getOrThrow().verifyJws(jws).getOrThrow()
            } else {
                ClientIdCrypto2.verify(jws, ClientIdCrypto2.keyFromJwk(cnfJwk, clientId.sub))
            }

            log.debug { "verifier_attestation: successfully validated for client_id=${clientId.sub}" }

            ClientValidationResult.Success(context.clientMetadata
                ?: throw IllegalArgumentException("client_metadata parameter is required for verifier_attestation")
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            ClientValidationResult.Failure(ClientIdError.AttestationError(cause.message ?: "Unknown error"))
        }
    }
}
