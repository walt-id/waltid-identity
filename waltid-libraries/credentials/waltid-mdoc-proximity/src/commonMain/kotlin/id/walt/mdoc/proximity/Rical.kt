@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.Cose
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ExactCbor
import id.walt.mdoc.objects.MdocVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.decodeFromByteArray
import kotlin.time.Instant

/** Informative edition-2 RICAL data retained behind an explicit provider and profile policy. */
@Serializable(with = RicalSerializer::class)
data class Rical(
    val version: String,
    val provider: String,
    val date: Instant,
    val nextUpdate: Instant? = null,
    val notAfter: Instant? = null,
    val certificateInfos: List<RicalCertificateInfo>,
    val id: ULong? = null,
    val latestRicalUrl: String? = null,
    val type: String,
    val extensions: Map<String, CborElement> = emptyMap(),
    val reserved: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(MdocVersion.parse(version).major == 1u) { "Unsupported RICAL major version" }
        require(provider.isNotBlank() && type.isNotBlank())
        require(certificateInfos.isNotEmpty()) { "RICAL certificateInfos must not be empty" }
        require(certificateInfos.any { it.isTrustAnchor }) { "RICAL must contain at least one trust anchor" }
        require(certificateInfos.map { it.subjectKeyIdentifier }.distinct().size == certificateInfos.size) {
            "RICAL subject key identifiers must be unique"
        }
        require(certificateInfos.all { it.reachesTrustAnchor(certificateInfos) }) {
            "Every RICAL CA must reach an included trust anchor"
        }
        require(nextUpdate == null || nextUpdate > date)
        require(notAfter == null || notAfter > date)
        require(latestRicalUrl == null || latestRicalUrl.startsWith("https://"))
        require(reserved.keys.none { it in RICAL_FIELD_NAMES })
    }

    companion object {
        private val RICAL_FIELD_NAMES = setOf(
            "version", "provider", "date", "nextUpdate", "notAfter", "certificateInfos", "id",
            "latestRicalUrl", "extensions", "type",
        )
    }
}

data class RicalCertificateInfo(
    val certificateDer: ImmutableBytes,
    val serialNumber: ImmutableBytes,
    val subjectKeyIdentifier: ImmutableBytes,
    val isTrustAnchor: Boolean,
    val authorityKeyIdentifier: ImmutableBytes? = null,
    val type: String? = null,
    val trustConstraints: List<RicalTrustConstraint> = emptyList(),
    val name: String? = null,
    val issuingCountry: String? = null,
    val stateOrProvinceName: String? = null,
    val issuerDer: ImmutableBytes? = null,
    val subjectDer: ImmutableBytes? = null,
    val notBefore: Instant? = null,
    val notAfter: Instant? = null,
    val extensions: Map<String, CborElement> = emptyMap(),
    val reserved: Map<String, CborElement> = emptyMap(),
) {
    init {
        require(certificateDer.size > 0 && serialNumber.size > 0 && subjectKeyIdentifier.size > 0)
        require(authorityKeyIdentifier == null || authorityKeyIdentifier.size > 0)
        require(isTrustAnchor || authorityKeyIdentifier != null) {
            "A RICAL sub-CA must contain an authority key identifier"
        }
        require(notBefore == null || notAfter == null || notAfter > notBefore)
        require(reserved.keys.none { it in CERTIFICATE_INFO_FIELD_NAMES })
    }

    private companion object {
        val CERTIFICATE_INFO_FIELD_NAMES = setOf(
            "certificate", "serialNumber", "isTrustAnchor", "ski", "aki", "type", "trustConstraints",
            "trustContraints", "name", "issuingCountry", "stateOrProvinceName", "issuer", "subject",
            "notBefore", "notAfter", "extensions",
        )
    }
}

private fun RicalCertificateInfo.reachesTrustAnchor(all: List<RicalCertificateInfo>): Boolean {
    var current = this
    val visited = mutableSetOf<ImmutableBytes>()
    while (visited.add(current.subjectKeyIdentifier)) {
        if (current.isTrustAnchor) return true
        val authority = current.authorityKeyIdentifier ?: return false
        current = all.singleOrNull { it.subjectKeyIdentifier == authority } ?: return false
    }
    return false
}

data class RicalTrustConstraint(
    val values: Map<String, CborElement>,
) {
    init {
        require(this.values.isNotEmpty()) { "RICAL trust constraint must not be empty" }
        require(this.values.keys.all { it.isNotBlank() }) { "RICAL trust constraint keys must not be blank" }
    }
}

/** A COSE_Sign1 whose attached payload is decoded and retained as the exact RICAL bytes it authenticates. */
class SignedRical private constructor(
    encodedMessage: ByteArray,
    val payload: ExactCbor<Rical>,
    signerChainDer: List<ImmutableBytes>,
) {
    private val messageBytes = ImmutableBytes.of(encodedMessage)
    val signerChainDer: List<ImmutableBytes> = signerChainDer.toList()
    val rical: Rical get() = payload.value
    val coseSign1: CoseSign1 get() = CoseSign1.fromTagged(messageBytes.copy())
    val exactMessage: ImmutableBytes get() = messageBytes

    init {
        require(this.signerChainDer.isNotEmpty()) { "Signed RICAL must contain a provider certificate chain" }
    }

    companion object {
        fun decode(encodedMessage: ByteArray): SignedRical {
            require(encodedMessage.firstOrNull() == 0x84.toByte()) {
                "RICAL must use an untagged COSE_Sign1 message"
            }
            return create(CoseSign1.fromTagged(encodedMessage), encodedMessage)
        }

        fun fromCoseSign1(message: CoseSign1): SignedRical = create(message, message.serialize())

        private fun create(message: CoseSign1, encodedMessage: ByteArray): SignedRical {
            val payloadBytes = requireNotNull(message.payload) { "RICAL COSE_Sign1 must use an attached payload" }
            val rical = coseCompliantCbor.decodeFromByteArray<Rical>(payloadBytes)
            val protected = if (message.protected.isEmpty()) CoseHeaders()
                else coseCompliantCbor.decodeFromByteArray(CoseHeaders.serializer(), message.protected)
            require(protected.algorithm in RICAL_SIGNATURE_ALGORITHMS) {
                "RICAL protected algorithm must be ES256, ES384, ES512, or EdDSA"
            }
            require(message.unprotected.algorithm == null) { "RICAL algorithm must not be unprotected" }
            require(message.unprotected.x5chain == null) { "RICAL x5chain must be protected" }
            val chain = protected.x5chain
                ?: throw IllegalArgumentException("RICAL COSE_Sign1 has no protected provider x5chain")
            return SignedRical(
                encodedMessage,
                ExactCbor.of(rical, payloadBytes),
                chain.map { ImmutableBytes.of(it.rawBytes) },
            )
        }

        private val RICAL_SIGNATURE_ALGORITHMS = setOf(
            Cose.Algorithm.ES256,
            Cose.Algorithm.ES384,
            Cose.Algorithm.ES512,
            Cose.Algorithm.EdDSA,
        )
    }
}

sealed interface RicalProviderResult {
    data class Available(val signed: SignedRical) : RicalProviderResult
    data class Unavailable(val reason: String) : RicalProviderResult {
        init {
            require(reason.isNotBlank())
        }
    }

    data class Conflict(val reason: String) : RicalProviderResult {
        init {
            require(reason.isNotBlank())
        }
    }
}

/** Supplies the provider's latest validated candidate without hiding unavailable or conflicting state. */
fun interface RicalProvider {
    suspend fun current(): RicalProviderResult
}

fun interface RicalSignatureValidator {
    suspend fun validate(signed: SignedRical, trustedProviderRootsDer: List<ImmutableBytes>): Boolean
}

fun interface RicalConstraintEvaluator {
    suspend fun accepts(constraints: List<RicalTrustConstraint>, reader: ReaderAuthenticationEvidence): Boolean
}

sealed interface RicalReaderPathResult {
    data object NoMatch : RicalReaderPathResult
    data object Invalid : RicalReaderPathResult
    data object Revoked : RicalReaderPathResult
    data class Valid(val authority: RicalCertificateInfo) : RicalReaderPathResult
}

/**
 * Profile-owned RFC 5280/path/revocation validation against the complete active RICAL.
 * A valid result contains only the bottom-most matching authority whose constraints apply.
 */
fun interface RicalReaderPathValidator {
    suspend fun validate(
        reader: ReaderAuthenticationEvidence,
        rical: Rical,
    ): RicalReaderPathResult
}

data class RicalPolicy(
    val providerId: String,
    val acceptedTypes: Set<String>,
    val trustedProviderRootsDer: List<ImmutableBytes>,
    val establishReaderTrust: Boolean = false,
) {
    init {
        require(providerId.isNotBlank() && acceptedTypes.isNotEmpty() && trustedProviderRootsDer.isNotEmpty())
        require(acceptedTypes.none(String::isBlank))
    }
}

sealed interface RicalUpdateDecision {
    data object Initial : RicalUpdateDecision
    data object Accepted : RicalUpdateDecision
    data object Unchanged : RicalUpdateDecision
    data class Stale(val reason: String) : RicalUpdateDecision
    data class Conflict(val reason: String) : RicalUpdateDecision
}

/** Applies the DIS version/id/date ordering rules without treating `nextUpdate` as an expiry. */
fun validateRicalUpdate(previous: SignedRical?, candidate: SignedRical): RicalUpdateDecision {
    previous ?: return RicalUpdateDecision.Initial
    val old = previous.rical
    val new = candidate.rical
    if (new.provider != old.provider || new.type != old.type) {
        return RicalUpdateDecision.Conflict("RICAL provider and type must remain stable across an update")
    }
    val oldVersion = MdocVersion.parse(old.version)
    val newVersion = MdocVersion.parse(new.version)
    if (newVersion < oldVersion) return RicalUpdateDecision.Stale("RICAL version moved backwards")
    if (newVersion.major != oldVersion.major) {
        return RicalUpdateDecision.Conflict("RICAL major version changed")
    }
    val exactSame = previous.payload == candidate.payload
    if (old.id != null || new.id != null) {
        if (old.id == null || new.id == null) {
            return RicalUpdateDecision.Conflict("RICAL update identifier presence changed")
        }
        return when {
            new.id < old.id -> RicalUpdateDecision.Stale("RICAL update identifier moved backwards")
            new.id == old.id && exactSame -> RicalUpdateDecision.Unchanged
            new.id == old.id -> RicalUpdateDecision.Conflict("Different RICAL payloads use the same update identifier")
            new.date < old.date -> RicalUpdateDecision.Conflict("A newer RICAL identifier has an older issue date")
            else -> RicalUpdateDecision.Accepted
        }
    }
    return when {
        new.date < old.date -> RicalUpdateDecision.Stale("RICAL issue date moved backwards")
        new.date == old.date && exactSame -> RicalUpdateDecision.Unchanged
        new.date == old.date -> RicalUpdateDecision.Conflict("Different RICAL payloads use the same issue date")
        else -> RicalUpdateDecision.Accepted
    }
}

class RicalReaderTrustEvaluator(
    private val provider: RicalProvider,
    private val policy: RicalPolicy,
    private val signatureValidator: RicalSignatureValidator,
    private val constraintEvaluator: RicalConstraintEvaluator,
    private val now: () -> Instant,
    private val pathValidator: RicalReaderPathValidator,
) : ReaderTrustEvaluator {
    override suspend fun evaluate(evidence: ReaderAuthenticationEvidence): ReaderTrustDecision =
        evaluateDetailed(evidence).decision

    suspend fun evaluateDetailed(evidence: ReaderAuthenticationEvidence): RicalReaderTrustResult {
        val signed = when (val result = provider.current()) {
            is RicalProviderResult.Available -> result.signed
            is RicalProviderResult.Unavailable -> return RicalReaderTrustResult(
                RicalEvaluationState.UNAVAILABLE,
                ReaderTrustDecision(
                    ReaderTrustState.VALID_BUT_UNTRUSTED,
                    "RICAL provider is unavailable: ${result.reason}",
                ),
            )
            is RicalProviderResult.Conflict -> return RicalReaderTrustResult(
                RicalEvaluationState.INVALID,
                ReaderTrustDecision(
                    ReaderTrustState.VALID_BUT_UNTRUSTED,
                    "RICAL provider has conflicting active data: ${result.reason}",
                ),
            )
        }
        val rical = signed.rical
        if (rical.provider != policy.providerId || rical.type !in policy.acceptedTypes) {
            return invalid("RICAL provider or type is not permitted")
        }
        val current = now()
        if (rical.date > current || rical.notAfter?.let { current >= it } == true) {
            return invalid("RICAL is not currently fresh")
        }
        if (!signatureValidator.validate(signed, policy.trustedProviderRootsDer)) {
            return invalid("RICAL signature or provider path is invalid")
        }
        val authority = when (val path = pathValidator.validate(evidence, rical)) {
            RicalReaderPathResult.NoMatch -> return noMatchingAuthority()
            RicalReaderPathResult.Invalid -> return invalid("Reader certificate path through the RICAL is invalid")
            RicalReaderPathResult.Revoked -> return RicalReaderTrustResult(
                RicalEvaluationState.MATCHED,
                ReaderTrustDecision(
                    ReaderTrustState.REVOKED,
                    "Reader authentication certificate is revoked",
                ),
            )
            is RicalReaderPathResult.Valid -> path.authority.takeIf { it in rical.certificateInfos }
                ?: return invalid("Reader path selected an authority outside the active RICAL")
        }
        if (!constraintEvaluator.accepts(authority.trustConstraints, evidence)) {
            return noMatchingAuthority()
        }
        val decision = if (policy.establishReaderTrust) {
            ReaderTrustDecision(ReaderTrustState.TRUSTED, displayName = authority.name)
        } else {
            ReaderTrustDecision(
                ReaderTrustState.VALID_BUT_UNTRUSTED,
                "RICAL evidence is valid but the active policy does not establish reader trust",
                authority.name,
            )
        }
        return RicalReaderTrustResult(
            RicalEvaluationState.MATCHED,
            decision,
        )
    }

    private fun invalid(reason: String): RicalReaderTrustResult = RicalReaderTrustResult(
        RicalEvaluationState.INVALID,
        ReaderTrustDecision(ReaderTrustState.VALID_BUT_UNTRUSTED, reason),
    )

    private fun noMatchingAuthority(): RicalReaderTrustResult = RicalReaderTrustResult(
        RicalEvaluationState.NO_MATCHING_AUTHORITY,
        ReaderTrustDecision(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            "Reader does not satisfy a RICAL authority and its constraints",
        ),
    )
}

enum class RicalEvaluationState { UNAVAILABLE, INVALID, NO_MATCHING_AUTHORITY, MATCHED }

data class RicalReaderTrustResult(
    val state: RicalEvaluationState,
    val decision: ReaderTrustDecision,
)
