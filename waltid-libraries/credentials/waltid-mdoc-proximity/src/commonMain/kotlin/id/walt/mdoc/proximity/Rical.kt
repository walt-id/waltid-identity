@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import id.walt.mdoc.objects.MdocVersion
import kotlinx.serialization.cbor.CborElement
import kotlin.time.Instant

/** Informative edition-2 RICAL data retained behind an explicit provider and profile policy. */
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
        require(nextUpdate == null || nextUpdate > date)
        require(notAfter == null || notAfter > date)
        require(latestRicalUrl == null || latestRicalUrl.startsWith("https://"))
        require(reserved.keys.none { it in RICAL_FIELD_NAMES })
    }

    private companion object {
        val RICAL_FIELD_NAMES = setOf(
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

data class RicalTrustConstraint(
    val extensions: Map<String, CborElement>,
    val reserved: Map<String, CborElement> = emptyMap(),
)

data class SignedRical(
    val rical: Rical,
    val exactPayload: ImmutableBytes,
    val signerChainDer: List<ImmutableBytes>,
    val signature: ImmutableBytes,
)

fun interface RicalProvider {
    suspend fun current(): SignedRical?
}

fun interface RicalSignatureValidator {
    suspend fun validate(signed: SignedRical, trustedProviderRootsDer: List<ImmutableBytes>): Boolean
}

fun interface RicalConstraintEvaluator {
    suspend fun accepts(constraints: List<RicalTrustConstraint>, reader: ReaderAuthenticationEvidence): Boolean
}

enum class RicalReaderPathState { NO_MATCH, INVALID, REVOKED, VALID }

/** Profile-owned RFC 5280/path/revocation validation against the complete active RICAL. */
fun interface RicalReaderPathValidator {
    suspend fun validate(
        reader: ReaderAuthenticationEvidence,
        rical: Rical,
        matchingCertificateInfo: RicalCertificateInfo,
    ): RicalReaderPathState
}

data class RicalPolicy(
    val providerId: String,
    val acceptedTypes: Set<String>,
    val trustedProviderRootsDer: List<ImmutableBytes>,
    val establishReaderTrust: Boolean = false,
) {
    init {
        require(providerId.isNotBlank() && acceptedTypes.isNotEmpty() && trustedProviderRootsDer.isNotEmpty())
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
    override suspend fun evaluate(evidence: ReaderAuthenticationEvidence): ReaderTrustDecision {
        val signed = provider.current() ?: return ReaderTrustDecision(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            "RICAL provider is unavailable",
        )
        val rical = signed.rical
        if (rical.provider != policy.providerId || rical.type !in policy.acceptedTypes) {
            return ReaderTrustDecision(ReaderTrustState.VALID_BUT_UNTRUSTED, "RICAL provider or type is not permitted")
        }
        val current = now()
        if (rical.date > current || rical.notAfter?.let { current >= it } == true) {
            return ReaderTrustDecision(ReaderTrustState.VALID_BUT_UNTRUSTED, "RICAL is not currently fresh")
        }
        if (!signatureValidator.validate(signed, policy.trustedProviderRootsDer)) {
            return ReaderTrustDecision(ReaderTrustState.VALID_BUT_UNTRUSTED, "RICAL signature or provider path is invalid")
        }
        var authority: RicalCertificateInfo? = null
        for (info in rical.certificateInfos) {
            when (pathValidator.validate(evidence, rical, info)) {
                RicalReaderPathState.NO_MATCH, RicalReaderPathState.INVALID -> Unit
                RicalReaderPathState.REVOKED -> return ReaderTrustDecision(
                    ReaderTrustState.REVOKED,
                    "Reader authentication certificate is revoked",
                )
                RicalReaderPathState.VALID -> if (constraintEvaluator.accepts(info.trustConstraints, evidence)) {
                    authority = info
                    break
                }
            }
        }
        authority ?: return ReaderTrustDecision(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            "Reader does not satisfy a RICAL authority and its constraints",
        )
        return if (policy.establishReaderTrust) ReaderTrustDecision(ReaderTrustState.TRUSTED, displayName = authority.name)
        else ReaderTrustDecision(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            "RICAL evidence is valid but the active policy does not establish reader trust",
            authority.name,
        )
    }
}
