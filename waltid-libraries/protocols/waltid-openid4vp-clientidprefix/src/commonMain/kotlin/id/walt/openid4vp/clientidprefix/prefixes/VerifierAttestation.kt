package id.walt.openid4vp.clientidprefix.prefixes

import kotlinx.serialization.Serializable

/**
 * Handles `verifier_attestation` prefix per OpenID4VP 1.0, Section 5.9.3 and 12.
 */
@Serializable
data class VerifierAttestation(val sub: String, override val rawValue: String) : ClientId {

    // TODO:
    /*suspend fun authenticateVerifierAttestation(clientId: VerifierAttestation, context: RequestContext): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        return runCatching {
            // 1. Get the attestation JWT from the 'jwt' header of the main JWS.
            val attestationJwtString = jws.decodeJws().header["jwt"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing 'jwt' header containing the Verifier Attestation.")

            // 2. Get the issuer's key from a trusted, pre-configured source.
            val attestationIssuer = attestationJwtString.decodeJws().payload["iss"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Attestation JWT is missing 'iss' claim.")

            // val issuerKey = TrustedAttestationIssuerService.getKeyFor(attestationIssuer).getOrThrow()
            val issuerKey: Key

            // 3. Verify the attestation JWT's signature.
            val attestationPayload = issuerKey.verifyJws(attestationJwtString).getOrThrow().jsonObject

            // 4. Validate claims and extract the verifier's public key from the 'cnf' claim.
            val sub = attestationPayload["sub"]?.jsonPrimitive?.content
            val cnfJwkString = attestationPayload["cnf"]?.jsonObject?.get("jwk")?.toString()
                ?: throw IllegalStateException("Missing 'cnf' claim in attestation.")

            if (sub != clientId.sub) {
                throw IllegalArgumentException("Attestation 'sub' claim does not match client_id.")
            }
            val verifierPublicKey = JWKKey.importJWK(cnfJwkString).getOrThrow()

            // 5. Verify the main request JWS with the key from the attestation (Proof of Possession).
            verifierPublicKey.verifyJws(jws).getOrThrow()

            val metadataJson = context.clientMetadataJson
                ?: throw IllegalStateException("client_metadata parameter is required.")

            ClientMetadata.fromJson(metadataJson).getOrThrow()
        }.fold(
            onSuccess = { ClientValidationResult.Success(it) },
            onFailure = { ClientValidationResult.Failure(ClientIdError.AttestationError(it.message!!)) }
        )
    }*/

}
