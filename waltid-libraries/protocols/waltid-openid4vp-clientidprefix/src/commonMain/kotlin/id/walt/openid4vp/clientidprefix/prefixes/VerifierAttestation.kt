package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.credentials.keyresolver.JwtKeyResolver
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    ): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        return runCatching {
            val decodedRequest = jws.decodeJws()

            // 1. Extract the Verifier Attestation JWT from the `jwt` JOSE header
            val attestationJwtString = decodedRequest.header["jwt"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException(
                    "Signed request is missing the 'jwt' JOSE header required for verifier_attestation"
                )

            val decodedAttestation = attestationJwtString.decodeJws()

            // Validate typ header per OID4VP §12
            val typ = decodedAttestation.header["typ"]?.jsonPrimitive?.contentOrNull
            if (typ != EXPECTED_TYP) {
                throw IllegalArgumentException(
                    "Verifier Attestation JWT has incorrect 'typ': expected '$EXPECTED_TYP', got '$typ'"
                )
            }

            // 2. Resolve the attestation issuer's public key and verify the attestation JWT signature
            val attestationIssuerKey = JwtKeyResolver.resolveFromJwt(
                jwtHeader = decodedAttestation.header,
                jwtPayload = decodedAttestation.payload,
            ) ?: throw IllegalArgumentException(
                "Could not resolve public key for Verifier Attestation issuer " +
                    "(iss=${decodedAttestation.payload["iss"]?.jsonPrimitive?.contentOrNull})"
            )

            attestationIssuerKey.verifyJws(attestationJwtString).getOrElse {
                throw IllegalArgumentException("Verifier Attestation JWT signature is invalid: ${it.message}")
            }

            // 3. Validate that `sub` matches the client_id
            val attestationSub = decodedAttestation.payload["sub"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("Verifier Attestation JWT is missing 'sub' claim")

            if (attestationSub != clientId.sub) {
                throw IllegalArgumentException(
                    "Verifier Attestation 'sub' ($attestationSub) does not match client_id (${clientId.sub})"
                )
            }

            // 4. Extract verifier's public key from cnf.jwk
            val cnf = decodedAttestation.payload["cnf"]?.jsonObject
                ?: throw IllegalArgumentException("Verifier Attestation JWT is missing 'cnf' claim")
            val cnfJwk = cnf["jwk"]?.jsonObject
                ?: throw IllegalArgumentException("Verifier Attestation JWT 'cnf' is missing 'jwk' member")

            val verifierPublicKey = JWKKey.importJWK(cnfJwk.toString()).getOrElse {
                throw IllegalArgumentException("Could not parse verifier public key from 'cnf.jwk': ${it.message}")
            }

            // 5. Verify the main request JWS with the verifier's key (proof of possession)
            verifierPublicKey.verifyJws(jws).getOrElse {
                throw IllegalArgumentException(
                    "Request object signature verification failed against verifier public key from attestation: ${it.message}"
                )
            }

            log.debug { "verifier_attestation: successfully validated for client_id=${clientId.sub}" }

            context.clientMetadata
                ?: throw IllegalArgumentException("client_metadata parameter is required for verifier_attestation")
        }.fold(
            onSuccess = { ClientValidationResult.Success(it) },
            onFailure = { ClientValidationResult.Failure(ClientIdError.AttestationError(it.message ?: "Unknown error")) }
        )
    }
}
