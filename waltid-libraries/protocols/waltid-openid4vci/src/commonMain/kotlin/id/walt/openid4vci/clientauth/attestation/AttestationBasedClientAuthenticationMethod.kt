package id.walt.openid4vci.clientauth.attestation

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vci.clientauth.AuthenticatedClient
import id.walt.openid4vci.clientauth.ClientAuthenticationContext
import id.walt.openid4vci.clientauth.ClientAuthenticationEndpoint
import id.walt.openid4vci.clientauth.ClientAuthenticationMethod
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.ClientAuthenticationResult
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerificationResult
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifier
import id.walt.openid4vci.clientauth.attestation.verifier.KeyBasedClientAttestationVerifier
import id.walt.openid4vci.clientauth.oauthHeaderValues
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.errors.OAuthErrorCodes
import id.walt.openid4vci.tokens.jwt.JwtConfirmationClaims
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

class AttestationBasedClientAuthenticationMethod(
    private val attestationVerifier: ClientAttestationVerifier,
    private val acceptedAttestationSigningAlgorithms: Set<String> =
        ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
    private val acceptedPopSigningAlgorithms: Set<String> =
        ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
    private val clock: () -> Instant = { Clock.System.now() },
    private val clockSkewSeconds: Long = 60,
    private val popMaxAgeSeconds: Long = 300,
) : ClientAuthenticationMethod {

    constructor(
        trustedAttesterKeys: suspend (
            header: JsonObject,
            payload: JsonObject,
        ) -> List<Key>,
        acceptedAttestationSigningAlgorithms: Set<String> =
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
        acceptedPopSigningAlgorithms: Set<String> =
            ClientAttestationSigningAlgorithms.SUPPORTED_JWS_ALGORITHMS,
        clock: () -> Instant = { Clock.System.now() },
        clockSkewSeconds: Long = 60,
        popMaxAgeSeconds: Long = 300,
    ) : this(
        attestationVerifier = KeyBasedClientAttestationVerifier(trustedAttesterKeys),
        acceptedAttestationSigningAlgorithms = acceptedAttestationSigningAlgorithms,
        acceptedPopSigningAlgorithms = acceptedPopSigningAlgorithms,
        clock = clock,
        clockSkewSeconds = clockSkewSeconds,
        popMaxAgeSeconds = popMaxAgeSeconds,
    )

    init {
        require(acceptedAttestationSigningAlgorithms.isNotEmpty()) {
            "acceptedAttestationSigningAlgorithms must not be empty"
        }
        require(acceptedPopSigningAlgorithms.isNotEmpty()) {
            "acceptedPopSigningAlgorithms must not be empty"
        }
        require(clockSkewSeconds >= 0) { "clockSkewSeconds must not be negative" }
        require(popMaxAgeSeconds > 0) { "popMaxAgeSeconds must be positive" }
    }

    override val name: String = ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH

    @Suppress("UNUSED_PARAMETER")
    override suspend fun authenticate(
        endpoint: ClientAuthenticationEndpoint,
        parameters: Map<String, List<String>>,
        headers: Map<String, List<String>>,
        context: ClientAuthenticationContext,
    ): ClientAuthenticationResult {
        val attestationJwt = singleHeader(headers, ClientAttestationHeaders.CLIENT_ATTESTATION)
            ?: return failure("Exactly one ${ClientAttestationHeaders.CLIENT_ATTESTATION} header is required")
        val popJwt = singleHeader(headers, ClientAttestationHeaders.CLIENT_ATTESTATION_POP)
            ?: return failure("Exactly one ${ClientAttestationHeaders.CLIENT_ATTESTATION_POP} header is required")

        val now = clock()
        val expectedAudience = context.authorizationServerIssuer?.takeIf { it.isNotBlank() }
            ?: return failure("Authorization server issuer is required for client attestation authentication")

        val attestation = decodeJwt(attestationJwt) ?: return invalidJwt("client attestation")
        val attestationError = validateAttestation(attestation, now)
        if (attestationError != null) return failure(attestationError)

        val clientId = attestation.payload.stringClaim(JwtPayloadClaims.SUBJECT)
            ?: return failure("Client attestation ${JwtPayloadClaims.SUBJECT} claim is required")

        val requestedClientIds = parameters["client_id"].orEmpty()
        if (requestedClientIds.size > 1) {
            return failure("Exactly one client_id parameter is allowed for client attestation authentication")
        }
        val requestedClientId = requestedClientIds.singleOrNull()
        if (requestedClientId != null && requestedClientId != clientId) {
            return failure("client_id does not match client attestation subject")
        }

        val verification = attestationVerifier.verifyAttestationJwt(
            jwt = attestationJwt,
            header = attestation.header,
            payload = attestation.payload,
        )
        if (verification is ClientAttestationVerificationResult.Rejected) {
            return failure(verification.reason ?: "Client attestation is not trusted")
        }

        val confirmationJwk = (attestation.payload[JwtPayloadClaims.CONFIRMATION] as? JsonObject)
            ?.get(JwtConfirmationClaims.JWK) as? JsonObject
            ?: return failure(
                "Client attestation ${JwtPayloadClaims.CONFIRMATION}.${JwtConfirmationClaims.JWK} claim is required",
            )

        val clientInstanceKey = runCatching {
            JWKKey.importJWK(confirmationJwk.toString()).getOrThrow()
        }.getOrElse {
            return failure(
                "Client attestation ${JwtPayloadClaims.CONFIRMATION}.${JwtConfirmationClaims.JWK} is invalid",
            )
        }

        if (clientInstanceKey.hasPrivateKey) {
            return failure(
                "Client attestation ${JwtPayloadClaims.CONFIRMATION}.${JwtConfirmationClaims.JWK} must not contain " +
                    "a private key",
            )
        }

        val pop = decodeJwt(popJwt) ?: return invalidJwt("client attestation PoP")
        val popError = validatePop(
            jwt = pop,
            expectedAudience = expectedAudience,
            expectedChallenge = context.challenge,
            now = now,
        )
        if (popError != null) return failure(popError)

        clientInstanceKey.verifyJws(popJwt).getOrElse {
            return failure("Client attestation PoP signature is invalid")
        }

        return ClientAuthenticationResult.Authenticated(
            AuthenticatedClient(
                id = clientId,
                authenticationMethod = name,
                registered = false,
                confirmationJwk = confirmationJwk,
                claims = attestation.payload,
            ),
        )
    }

    private fun validateAttestation(jwt: DecodedJwt, now: Instant): String? {
        if (jwt.header.stringClaim(JwtHeaderParams.TYPE) != ClientAttestationJwtTypes.CLIENT_ATTESTATION) {
            return "Client attestation ${JwtHeaderParams.TYPE} header must be " +
                ClientAttestationJwtTypes.CLIENT_ATTESTATION
        }
        val alg = jwt.header.stringClaim(JwtHeaderParams.ALGORITHM)
            ?: return "Client attestation ${JwtHeaderParams.ALGORITHM} header is required"
        if (alg !in acceptedAttestationSigningAlgorithms) {
            return "Client attestation ${JwtHeaderParams.ALGORITHM} is not supported"
        }
        if (jwt.payload.stringClaim(JwtPayloadClaims.SUBJECT).isNullOrBlank()) {
            return "Client attestation ${JwtPayloadClaims.SUBJECT} claim is required"
        }
        val exp = jwt.payload.longClaim(JwtPayloadClaims.EXPIRATION)
            ?: return "Client attestation ${JwtPayloadClaims.EXPIRATION} claim is required"
        if (now.epochSeconds > exp + clockSkewSeconds) {
            return "Client attestation is expired"
        }
        val iat = jwt.payload.longClaim(JwtPayloadClaims.ISSUED_AT)
        if (iat != null && iat > now.epochSeconds + clockSkewSeconds) {
            return "Client attestation ${JwtPayloadClaims.ISSUED_AT} claim is in the future"
        }
        if (
            (jwt.payload[JwtPayloadClaims.CONFIRMATION] as? JsonObject)
                ?.get(JwtConfirmationClaims.JWK) !is JsonObject
        ) {
            return "Client attestation ${JwtPayloadClaims.CONFIRMATION}.${JwtConfirmationClaims.JWK} claim is required"
        }
        return null
    }

    private fun validatePop(
        jwt: DecodedJwt,
        expectedAudience: String,
        expectedChallenge: String?,
        now: Instant,
    ): String? {
        if (jwt.header.stringClaim(JwtHeaderParams.TYPE) != ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP) {
            return "Client attestation PoP ${JwtHeaderParams.TYPE} header must be " +
                ClientAttestationJwtTypes.CLIENT_ATTESTATION_POP
        }
        val alg = jwt.header.stringClaim(JwtHeaderParams.ALGORITHM)
            ?: return "Client attestation PoP ${JwtHeaderParams.ALGORITHM} header is required"
        if (alg !in acceptedPopSigningAlgorithms) {
            return "Client attestation PoP ${JwtHeaderParams.ALGORITHM} is not supported"
        }
        if (!jwt.payload.hasSingleAudience(expectedAudience)) {
            return "Client attestation PoP ${JwtPayloadClaims.AUDIENCE} claim must identify only " +
                "the authorization server issuer"
        }
        if (
            !expectedChallenge.isNullOrBlank() &&
            jwt.payload.stringClaim(JwtPayloadClaims.CHALLENGE) != expectedChallenge
        ) {
            return "Client attestation PoP ${JwtPayloadClaims.CHALLENGE} claim does not match the expected challenge"
        }
        if (jwt.payload.stringClaim(JwtPayloadClaims.JWT_ID).isNullOrBlank()) {
            return "Client attestation PoP ${JwtPayloadClaims.JWT_ID} claim is required"
        }
        val iat = jwt.payload.longClaim(JwtPayloadClaims.ISSUED_AT)
            ?: return "Client attestation PoP ${JwtPayloadClaims.ISSUED_AT} claim is required"
        val earliest = now.epochSeconds - popMaxAgeSeconds - clockSkewSeconds
        val latest = now.epochSeconds + clockSkewSeconds
        if (iat < earliest || iat > latest) {
            return "Client attestation PoP ${JwtPayloadClaims.ISSUED_AT} claim is outside the accepted age window"
        }
        return null
    }

    private fun singleHeader(headers: Map<String, List<String>>, name: String): String? {
        val values = headers.oauthHeaderValues(name)
        return when (values.size) {
            1 -> values.first()
            else -> null
        }
    }

    private fun decodeJwt(jwt: String): DecodedJwt? =
        runCatching {
            val decoded = jwt.decodeJws()
            DecodedJwt(header = decoded.header, payload = decoded.payload)
        }.getOrNull()

    private fun invalidJwt(label: String): ClientAuthenticationResult.Failure =
        failure("Invalid $label JWT")

    private fun failure(description: String): ClientAuthenticationResult.Failure =
        ClientAuthenticationResult.Failure(OAuthError(OAuthErrorCodes.INVALID_CLIENT, description))

    private fun JsonObject.stringClaim(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.longClaim(name: String): Long? =
        (this[name] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.hasSingleAudience(expectedAudience: String): Boolean {
        val aud = this[JwtPayloadClaims.AUDIENCE] ?: return false
        return when (aud) {
            is JsonArray -> aud.size == 1 && aud.firstOrNull()?.stringValue() == expectedAudience
            is JsonPrimitive -> aud.contentOrNull == expectedAudience
            else -> false
        }
    }

    private fun JsonElement.stringValue(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private data class DecodedJwt(
        val header: JsonObject,
        val payload: JsonObject,
    )
}
