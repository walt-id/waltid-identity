package id.walt.openid4vci.attestation

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.Jwk
import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.proofs.attestation.restoreAttestedKey
import id.walt.openid4vci.requests.credential.AcceptedCredentialRequest
import id.walt.statuslist.token.IetfStatusListToken
import io.ktor.http.Url
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Instant

/** Status requirements are independent of the requirement to present an attestation. */
@Serializable
data class AttestationStatusPolicy(
    val requireStatusReference: Boolean = false,
    /** Optional extra age bound from signed iat; signed validity checks apply with either setting. */
    val maximumListAgeSeconds: Long? = null,
) {
    init {
        require(maximumListAgeSeconds == null || maximumListAgeSeconds > 0) {
            "maximumListAgeSeconds must be positive when configured"
        }
    }
}

@Serializable
data class AttestationStatusReference(val uri: String, val index: Int)

/**
 * Private, verified evidence. Null evidence means an older verifier did not capture status.
 * Version defaults to 1 for old snapshots that omitted it; new verification captures version 2.
 * Version 1 retains its implicit 300-second age limit, while version 2 permits an absent age cap.
 */
@Serializable
data class VerifiedAttestationStatus(
    val version: Int = 1,
    val capturedAt: Long,
    val policy: AttestationStatusPolicy,
    val reference: AttestationStatusReference? = null,
    val attesterIssuer: String? = null,
    val attesterPublicJwk: JsonObject? = null,
)

/** Returns the signed JWT, never a previously computed VALID decision. */
fun interface AttestationStatusResolver {
    suspend fun resolve(uri: String): String
}

enum class AttestationStatusOutcome { VALID, INVALID, SUSPENDED, UNVERIFIABLE, MISSING_EVIDENCE }

data class AttestationStatusResult(val outcome: AttestationStatusOutcome) {
    val permanentlyDenied: Boolean
        get() = outcome == AttestationStatusOutcome.INVALID || outcome == AttestationStatusOutcome.MISSING_EVIDENCE

    fun error(): CredentialError? = when (outcome) {
        AttestationStatusOutcome.VALID -> null
        AttestationStatusOutcome.INVALID -> CredentialError(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, "Accepted attestation has been revoked")
        AttestationStatusOutcome.MISSING_EVIDENCE -> CredentialError(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, "Required attestation status evidence is missing")
        AttestationStatusOutcome.SUSPENDED -> CredentialError("temporarily_unavailable", "Accepted attestation is suspended; retry the same transaction", retryAfterSeconds = 1)
        AttestationStatusOutcome.UNVERIFIABLE -> CredentialError("temporarily_unavailable", "Attestation status could not be verified; retry the same transaction", retryAfterSeconds = 1)
    }
}

/** Only call with payload and signer returned by successful attestation verification. */
internal fun captureAttestationStatus(
    payload: JsonObject,
    signer: JsonObject?,
    policy: AttestationStatusPolicy,
    now: Instant,
): VerifiedAttestationStatus {
    val reference = payload["status"]?.let { status ->
        val objectValue = status as? JsonObject ?: error("Attestation status must be an object")
        val list = objectValue["status_list"] as? JsonObject ?: error("Unsupported attestation status mechanism")
        val index = (list["idx"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            ?.takeIf { it >= 0 } ?: error("Attestation status idx must be a non-negative integer")
        val uri = (list["uri"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?.takeIf { it.isNotBlank() } ?: error("Attestation status uri must be a string")
        parseAttestationStatusListUrl(uri)
        AttestationStatusReference(uri, index)
    }
    require(!policy.requireStatusReference || reference != null) { "Attestation status reference is required" }
    require(signer == null || !Jwk.containsPrivateMaterial(signer)) { "Attester context must contain only public material" }
    return VerifiedAttestationStatus(
        version = 2, capturedAt = now.epochSeconds, policy = policy, reference = reference,
        attesterIssuer = (payload["iss"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
        attesterPublicJwk = signer,
    )
}

internal fun parseAttestationStatusListUrl(uri: String): Url {
    // Ktor supplies a localhost origin for relative references. Check the signed raw value first.
    val schemeEnd = uri.indexOf("://")
    require(schemeEnd > 0 && uri.substring(0, schemeEnd).lowercase() in setOf("http", "https")) {
        "Attestation status requires an absolute HTTP(S) URI"
    }
    val authority = uri.substring(schemeEnd + 3).takeWhile { it != '/' && it != '?' && it != '#' }
    require(authority.isNotBlank() && uri.none { it.isWhitespace() || it == '\\' || it == '#' }) {
        "Attestation status requires an authority and no whitespace, backslashes or fragment"
    }
    return Url(uri).also { url ->
        require(url.protocol.name in setOf("http", "https") && url.host.isNotBlank()) {
            "Attestation status requires an absolute HTTP(S) URI"
        }
    }
}

/** One invocation is one checkpoint. Lists are fetched afresh and deduplicated only within it. */
class AttestationStatusValidator(
    private val resolver: AttestationStatusResolver?,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val logger = KotlinLogging.logger {}

    suspend fun validate(
        evidence: List<Pair<VerifiedAttestationStatus?, Boolean>>,
    ): AttestationStatusResult {
        val lists = mutableMapOf<String, Result<ResolvedList>>()
        val validityChecks = mutableListOf<ListValidity>()
        var outcome = AttestationStatusOutcome.VALID
        for ((accepted, required) in evidence) {
            val next = if (accepted == null) {
                // Explicit compatibility: legacy optional-status profiles retain F5 lifetime checks.
                if (required) AttestationStatusOutcome.MISSING_EVIDENCE else AttestationStatusOutcome.VALID
            } else if (accepted.version !in 1..2) {
                AttestationStatusOutcome.MISSING_EVIDENCE
            } else if (accepted.reference == null) {
                if (accepted.policy.requireStatusReference || required) AttestationStatusOutcome.MISSING_EVIDENCE else AttestationStatusOutcome.VALID
            } else if (accepted.attesterPublicJwk == null) {
                AttestationStatusOutcome.UNVERIFIABLE
            } else {
                val uri = accepted.reference.uri
                val jwt = lists.getOrPut(uri) {
                    try {
                        parseAttestationStatusListUrl(uri)
                        val jwt = requireNotNull(resolver) { "Attestation status resolver is not configured" }.resolve(uri)
                        Result.success(ResolvedList(jwt, now()))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (cause: Exception) { Result.failure(cause) }
                }
                try {
                    validateList(jwt.getOrThrow(), accepted).also { validityChecks += it.validity }.outcome
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (cause: Exception) {
                    logger.debug(cause) { "Attestation status validation failed" }
                    AttestationStatusOutcome.UNVERIFIABLE
                }
            }
            // A confirmed revocation wins even when another list is temporarily unavailable.
            if (priority(next) > priority(outcome)) outcome = next
        }
        // Earlier lists may have expired while later lookups or signature checks were suspended.
        val checkedAt = now()
        if (validityChecks.any { !it.isValidAt(checkedAt) } && priority(AttestationStatusOutcome.UNVERIFIABLE) > priority(outcome)) {
            outcome = AttestationStatusOutcome.UNVERIFIABLE
        }
        return AttestationStatusResult(outcome)
    }

    private data class ResolvedList(val jwt: String, val resolvedAt: Instant)
    private data class ValidatedList(val outcome: AttestationStatusOutcome, val validity: ListValidity)
    private data class ListValidity(
        val claims: IetfStatusListToken.Validity,
        val resolvedAt: Instant,
        val maximumAgeSeconds: Long?,
    ) {
        fun isValidAt(current: Instant): Boolean = claims.isValidAt(current, resolvedAt, maximumAgeSeconds)
    }

    private suspend fun validateList(resolved: ResolvedList, evidence: VerifiedAttestationStatus): ValidatedList {
        val jwt = resolved.jwt
        require(jwt.length <= 2 * 1024 * 1024) { "Status-list JWT is too large" }
        val decoded = CompactJws.decodeUnverified(jwt)
        val signer = restoreAttestedKey(requireNotNull(evidence.attesterPublicJwk) { "Verified attester signer context is unavailable" })
        // Status-response jwk/kid/x5c never authorize a different key. Delegation is not enabled.
        CompactJws.verify(jwt, signer, JwsAlgorithm.parse(requireNotNull(decoded.protectedHeader["alg"]?.jsonPrimitive?.content)))
        val payload = Json.parseToJsonElement(decoded.payload.decodeToString()).jsonObject
        val reference = requireNotNull(evidence.reference)
        val list = IetfStatusListToken.fromJwt(decoded.protectedHeader, payload, reference.uri)
        // Version 1 could omit its former default of 300. A new configuration default must
        // not weaken that transaction's accepted policy when its snapshot is restored.
        val maximumAgeSeconds = if (evidence.version == 1) evidence.policy.maximumListAgeSeconds ?: 300L
            else evidence.policy.maximumListAgeSeconds
        val validity = ListValidity(list.validity, resolved.resolvedAt, maximumAgeSeconds)
        require(validity.isValidAt(now())) { "Status list is stale, expired or not yet valid" }
        val outcome = when (list.readStatus(reference.index)) {
            0u -> AttestationStatusOutcome.VALID
            1u -> AttestationStatusOutcome.INVALID
            2u -> AttestationStatusOutcome.SUSPENDED
            else -> AttestationStatusOutcome.UNVERIFIABLE
        }
        return ValidatedList(outcome, validity)
    }

    private fun priority(outcome: AttestationStatusOutcome): Int = when (outcome) {
        AttestationStatusOutcome.VALID -> 0
        AttestationStatusOutcome.SUSPENDED -> 1
        AttestationStatusOutcome.UNVERIFIABLE -> 2
        AttestationStatusOutcome.MISSING_EVIDENCE -> 3
        AttestationStatusOutcome.INVALID -> 4
    }
}

internal fun AcceptedCredentialRequest.statusEvidence(
    legacyClientStatusRequired: Boolean = false,
    legacyKeyStatusRequired: Boolean = false,
): List<Pair<VerifiedAttestationStatus?, Boolean>> = buildList {
    clientAttestation?.let { add(it.status to (it.status == null && legacyClientStatusRequired)) }
    if (clientAttestation == null && clientAttestationRequired == null && legacyClientStatusRequired && !request.anonymousPreAuthorizedAccess) {
        add(null to true)
    }
    if (proofs == null && legacyKeyStatusRequired) add(null to true)
    proofs.orEmpty().forEach { proof ->
        proof.keyAttestation?.let { add(it.status to (it.status == null && legacyKeyStatusRequired)) }
    }
}
