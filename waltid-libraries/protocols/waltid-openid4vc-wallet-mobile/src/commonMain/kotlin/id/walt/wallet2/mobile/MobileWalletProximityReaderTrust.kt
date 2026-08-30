@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.cose.coseCompliantCbor
import id.walt.certificate.x509.X509CertificateUtil
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ReaderAuthenticationEvidence
import id.walt.mdoc.proximity.ReaderTrustState
import id.walt.mdoc.proximity.RicalConstraintEvaluator
import id.walt.mdoc.proximity.RicalEvaluationState
import id.walt.mdoc.proximity.RicalPolicy
import id.walt.mdoc.proximity.RicalProvider
import id.walt.mdoc.proximity.RicalProviderResult
import id.walt.mdoc.proximity.RicalReaderTrustEvaluator
import id.walt.mdoc.proximity.RicalSignatureValidator
import id.walt.mdoc.proximity.RicalTrustConstraint
import id.walt.mdoc.proximity.SignedRical
import id.walt.mdoc.proximity.X509RicalReaderPathValidator
import id.walt.mdoc.proximity.X509RicalSignatureValidator
import id.walt.x509.CertificateDer
import id.walt.x509.mdocReaderAuthenticationCommonName
import id.walt.x509.validateMdocReaderAuthenticationCertificateChain
import id.walt.x509.validateMdocReaderAuthenticationCertificateProfile
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.encodeToByteArray
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.time.Instant

/** Explicit application-provisioned Reader CA certificate and optional display label. */
public data class MobileWalletProximityReaderTrustAnchor(
    /** DER certificate encoded as unpadded Base64URL. */
    public val certificateDerBase64Url: String,
    /** Display-safe authority label. This label is evidence and never establishes trust by itself. */
    public val displayName: String? = null,
) {
    init {
        require(runCatching { certificateDerBase64Url.trustCertificateDer() }.isSuccess) {
            "A reader trust anchor must be a DER X.509 certificate encoded as unpadded Base64URL"
        }
        require(displayName == null || displayName.isNotBlank())
    }
}

/** Result returned by an application-owned certificate-revocation source. */
public sealed interface MobileWalletProximityCertificateRevocationResult {
    /** The configured source established that the certificate is not revoked. */
    public data object Good : MobileWalletProximityCertificateRevocationResult

    /** The configured source established that the certificate is revoked. */
    public data class Revoked(
        /** Optional display-safe reason supplied by the configured revocation source. */
        public val reason: String? = null,
    ) : MobileWalletProximityCertificateRevocationResult {
        init {
            require(reason == null || reason.isNotBlank())
        }
    }

    /** The configured source could not establish current revocation status. */
    public data class Indeterminate(
        /** Display-safe reason why current revocation status could not be established. */
        public val reason: String,
    ) : MobileWalletProximityCertificateRevocationResult {
        init {
            require(reason.isNotBlank())
        }
    }
}

/** Explicit application boundary for OCSP, CRL, or another reader-certificate status source. */
public fun interface MobileWalletProximityReaderRevocationEvaluator {
    /** Evaluates the exact verified reader evidence without an implicit SDK network request. */
    public suspend fun evaluate(
        evidence: MobileWalletProximityReaderEvidence,
    ): MobileWalletProximityCertificateRevocationResult
}

/** Revocation behavior selected for reader trust. */
public sealed interface MobileWalletProximityReaderRevocationPolicy {
    /** Do not perform revocation lookup; the resulting trust fact remains `NotChecked`. */
    public data object NotChecked : MobileWalletProximityReaderRevocationPolicy

    /** Require the supplied source to return a conclusive result before the reader can be trusted. */
    public data class Check(
        /** Application-owned reader-certificate revocation source. */
        public val evaluator: MobileWalletProximityReaderRevocationEvaluator,
    ) : MobileWalletProximityReaderRevocationPolicy
}

/** Exact RICAL signer evidence passed to an application-owned revocation source. */
public data class MobileWalletProximityRicalSignerEvidence(
    /** Stable application-configured identifier for the RICAL provider. */
    public val providerId: String,
    /** DER certificates in leaf-first order, encoded as unpadded Base64URL. */
    public val certificateChainDerBase64Url: List<String>,
) {
    init {
        require(providerId.isNotBlank())
        require(certificateChainDerBase64Url.isNotEmpty())
        require(certificateChainDerBase64Url.all(String::isTrustBase64Url))
    }
}

/** Explicit application boundary for RICAL-signer OCSP, CRL, or another status source. */
public fun interface MobileWalletProximityRicalSignerRevocationEvaluator {
    /** Evaluates the verified signer chain for the selected RICAL provider. */
    public suspend fun evaluate(
        evidence: MobileWalletProximityRicalSignerEvidence,
    ): MobileWalletProximityCertificateRevocationResult
}

/** Revocation behavior selected for one RICAL provider's signer certificate. */
public sealed interface MobileWalletProximityRicalSignerRevocationPolicy {
    /** Do not perform a signer-revocation lookup. */
    public data object NotChecked : MobileWalletProximityRicalSignerRevocationPolicy

    /** Require the supplied source to establish that the RICAL signer is not revoked. */
    public data class Check(
        /** Application-owned RICAL-signer revocation source. */
        public val evaluator: MobileWalletProximityRicalSignerRevocationEvaluator,
    ) : MobileWalletProximityRicalSignerRevocationPolicy
}

/** Explicit application-provisioned root for one RICAL provider. */
public data class MobileWalletProximityRicalProviderTrustAnchor(
    /** DER certificate encoded as unpadded Base64URL. */
    public val certificateDerBase64Url: String,
) {
    init {
        require(runCatching { certificateDerBase64Url.trustCertificateDer() }.isSuccess) {
            "A RICAL provider trust anchor must be a DER X.509 certificate encoded as unpadded Base64URL"
        }
    }
}

/** Exact active RICAL supplied by an application-owned provider boundary. */
public sealed interface MobileWalletProximityRicalProviderResult {
    /** Untagged COSE_Sign1 bytes encoded as unpadded Base64URL. */
    public data class Available(
        /** Untagged COSE_Sign1 bytes encoded as unpadded Base64URL. */
        public val signedRicalBase64Url: String,
    ) : MobileWalletProximityRicalProviderResult {
        init {
            require(signedRicalBase64Url.isTrustBase64Url())
        }
    }

    /** No active list is available. */
    public data class Unavailable(
        /** Display-safe reason why the provider has no active list. */
        public val reason: String,
    ) : MobileWalletProximityRicalProviderResult {
        init {
            require(reason.isNotBlank())
        }
    }

    /** The application detected conflicting active list state. */
    public data class Conflict(
        /** Display-safe description of the conflicting provider state. */
        public val reason: String,
    ) : MobileWalletProximityRicalProviderResult {
        init {
            require(reason.isNotBlank())
        }
    }
}

/** Supplies the latest application-selected RICAL without an implicit SDK network request. */
public fun interface MobileWalletProximityRicalProvider {
    /** Returns the application's current provider result for this evaluation. */
    public suspend fun current(): MobileWalletProximityRicalProviderResult
}

/** One RICAL trust constraint, with each value CBOR-encoded as unpadded Base64URL. */
public data class MobileWalletProximityRicalTrustConstraint(
    /** Constraint name to its CBOR-encoded value, using unpadded Base64URL. */
    public val valuesCborBase64Url: Map<String, String>,
) {
    init {
        require(valuesCborBase64Url.isNotEmpty())
        require(valuesCborBase64Url.keys.none(String::isBlank))
        require(valuesCborBase64Url.values.all(String::isTrustBase64Url))
    }
}

/** Application-owned evaluator for ecosystem-specific RICAL trust-constraint semantics. */
public fun interface MobileWalletProximityRicalConstraintEvaluator {
    /** Returns true only when at least one complete constraint is understood and satisfied. */
    public suspend fun accepts(
        constraints: List<MobileWalletProximityRicalTrustConstraint>,
        reader: MobileWalletProximityReaderEvidence,
    ): Boolean
}

/** Immutable policy for one explicitly configured RICAL provider. */
public data class MobileWalletProximityRicalConfiguration(
    /** Stable application-configured provider identifier. */
    public val providerId: String,
    /** RICAL type identifiers accepted from this provider. */
    public val acceptedTypes: Set<String>,
    /** Explicit X.509 trust anchors accepted for this provider's signer. */
    public val providerTrustAnchors: List<MobileWalletProximityRicalProviderTrustAnchor>,
    /** Certificate-policy OIDs accepted on this provider's signer certificate. */
    public val acceptedSignerCertificatePolicyOids: Set<String>,
    /** Revocation behavior for this provider's verified signer certificate. */
    public val signerRevocationPolicy: MobileWalletProximityRicalSignerRevocationPolicy =
        MobileWalletProximityRicalSignerRevocationPolicy.NotChecked,
    /** Whether an accepted matching authority may establish product reader trust. */
    public val establishReaderTrust: Boolean = false,
    /** Application-owned source of the current signed RICAL. */
    public val provider: MobileWalletProximityRicalProvider,
    /** Null rejects any non-empty, ecosystem-specific constraint as unsupported. */
    public val constraintEvaluator: MobileWalletProximityRicalConstraintEvaluator? = null,
) {
    init {
        require(providerId.isNotBlank())
        require(acceptedTypes.isNotEmpty() && acceptedTypes.none(String::isBlank))
        require(providerTrustAnchors.isNotEmpty())
        require(
            providerTrustAnchors.distinctBy { it.certificateDerBase64Url }.size == providerTrustAnchors.size
        ) { "RICAL provider trust anchors must be unique" }
        require(
            acceptedSignerCertificatePolicyOids.isNotEmpty() &&
                acceptedSignerCertificatePolicyOids.none(String::isBlank)
        ) { "Accepted RICAL signer certificate-policy OIDs are required" }
    }
}

/**
 * Immutable shared reader-trust policy.
 *
 * Direct Reader CA anchors and RICAL provider roots are always application-provisioned. Certificates
 * carried by a reader or a RICAL are path inputs only and never become implicit SDK trust anchors.
 * RICAL providers are evaluated in configured order; the first matching authority owns the result.
 */
public data class MobileWalletProximityReaderTrustConfiguration(
    /** Explicit application-provisioned Reader CA trust anchors. */
    public val trustAnchors: List<MobileWalletProximityReaderTrustAnchor> = emptyList(),
    /** Ordered application-configured RICAL provider policies. */
    public val ricalProviders: List<MobileWalletProximityRicalConfiguration> = emptyList(),
    /** Revocation behavior for a reader chain trusted by a direct Reader CA anchor. */
    public val revocationPolicy: MobileWalletProximityReaderRevocationPolicy =
        MobileWalletProximityReaderRevocationPolicy.NotChecked,
) {
    init {
        require(trustAnchors.isNotEmpty() || ricalProviders.isNotEmpty()) {
            "At least one explicit Reader CA anchor or RICAL provider is required"
        }
        require(trustAnchors.distinctBy { it.certificateDerBase64Url }.size == trustAnchors.size) {
            "Reader CA trust anchors must be unique"
        }
        require(ricalProviders.distinctBy { it.providerId }.size == ricalProviders.size) {
            "RICAL provider identifiers must be unique"
        }
    }
}

/** Shared standards-profile, path, revocation, RICAL, and product-trust evaluator. */
public class MobileWalletProximityConfiguredReaderTrustEvaluator internal constructor(
    /** Immutable application-provisioned trust policy evaluated by this instance. */
    public val configuration: MobileWalletProximityReaderTrustConfiguration,
    private val now: () -> Instant,
) : MobileWalletProximityReaderTrustEvaluator {
    public constructor(
        configuration: MobileWalletProximityReaderTrustConfiguration,
    ) : this(configuration, { Clock.System.now() })

    override suspend fun evaluate(
        evidence: MobileWalletProximityReaderEvidence,
    ): MobileWalletProximityReaderTrustDecision {
        val evaluatedAt = now()
        val chain = runCatching { evidence.certificateChainDerBase64Url.map(String::trustCertificateDer) }
            .getOrElse { return invalidPathDecision() }
        val leaf = chain.first()
        val readerName = runCatching {
            validateMdocReaderAuthenticationCertificateProfile(leaf, evaluatedAt)
            mdocReaderAuthenticationCommonName(leaf)
        }.getOrElse { return invalidPathDecision() }

        configuration.trustAnchors.firstOrNull { anchor ->
            runCatching {
                validateMdocReaderAuthenticationCertificateChain(
                    leaf = leaf,
                    chain = chain.drop(1),
                    trustAnchors = listOf(anchor.certificateDerBase64Url.trustCertificateDer()),
                    now = evaluatedAt,
                )
            }.isSuccess
        }?.let { anchor ->
            return decisionForValidatedPath(
                evidence = evidence,
                displayName = anchor.displayName ?: readerName,
                rical = MobileWalletProximityRicalState.NotEvaluated,
                establishesTrust = true,
            )
        }

        var fallback: RicalFallback? = null
        for (rical in configuration.ricalProviders) {
            val result = evaluateRical(rical, evidence, evaluatedAt)
            result.matched?.let { matched ->
                return when (matched) {
                    is RicalMatch.Revoked -> MobileWalletProximityReaderTrustDecision(
                        state = MobileWalletProximityReaderTrustState.Revoked,
                        certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
                        revocation = MobileWalletProximityReaderRevocationState.Revoked,
                        rical = MobileWalletProximityRicalState.Matched,
                        displayName = matched.displayName ?: readerName,
                        reason = matched.reason ?: "Reader authentication certificate is revoked",
                    )
                    is RicalMatch.Valid -> decisionForValidatedPath(
                        evidence = evidence,
                        displayName = matched.displayName ?: readerName,
                        rical = MobileWalletProximityRicalState.Matched,
                        establishesTrust = matched.establishesTrust,
                    )
                }
            }
            fallback = fallback.prefer(result.fallback)
        }

        return MobileWalletProximityReaderTrustDecision(
            state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
            certificatePath = MobileWalletProximityReaderCertificatePathState.UnknownAuthority,
            rical = fallback?.state ?: MobileWalletProximityRicalState.NotEvaluated,
            reason = fallback?.reason ?: "Reader authentication is valid, but its authority is not configured",
        )
    }

    private suspend fun evaluateRical(
        configuration: MobileWalletProximityRicalConfiguration,
        evidence: MobileWalletProximityReaderEvidence,
        evaluatedAt: Instant,
    ): RicalAttempt {
        val evaluator = RicalReaderTrustEvaluator(
            provider = RicalProvider {
                when (val result = configuration.provider.current()) {
                    is MobileWalletProximityRicalProviderResult.Available -> RicalProviderResult.Available(
                        SignedRical.decode(result.signedRicalBase64Url.decodeTrustBase64Url())
                    )
                    is MobileWalletProximityRicalProviderResult.Unavailable ->
                        RicalProviderResult.Unavailable(result.reason)
                    is MobileWalletProximityRicalProviderResult.Conflict ->
                        RicalProviderResult.Conflict(result.reason)
                }
            },
            policy = RicalPolicy(
                providerId = configuration.providerId,
                acceptedTypes = configuration.acceptedTypes,
                trustedProviderRootsDer = configuration.providerTrustAnchors.map {
                    ImmutableBytes.of(it.certificateDerBase64Url.decodeTrustBase64Url())
                },
                establishReaderTrust = configuration.establishReaderTrust,
            ),
            signatureValidator = configuration.signatureValidator(evaluatedAt),
            constraintEvaluator = RicalConstraintEvaluator { constraints, _ ->
                if (constraints.isEmpty()) true
                else configuration.constraintEvaluator?.accepts(
                    constraints.map(RicalTrustConstraint::toPublic),
                    evidence,
                ) == true
            },
            now = { evaluatedAt },
            pathValidator = X509RicalReaderPathValidator { evaluatedAt },
        )
        val result = try {
            evaluator.evaluateDetailed(evidence.toRicalEvidence())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return RicalAttempt(
                fallback = RicalFallback(
                    MobileWalletProximityRicalState.Invalid,
                    "RICAL provider data is invalid",
                )
            )
        }
        return when (result.state) {
            RicalEvaluationState.MATCHED -> RicalAttempt(
                matched = if (result.decision.state == ReaderTrustState.REVOKED) {
                    RicalMatch.Revoked(result.decision.displayName, result.decision.reason)
                } else {
                    RicalMatch.Valid(
                        result.decision.displayName,
                        result.decision.state == ReaderTrustState.TRUSTED,
                    )
                }
            )
            RicalEvaluationState.UNAVAILABLE -> RicalAttempt(
                fallback = RicalFallback(
                    MobileWalletProximityRicalState.Unavailable,
                    result.decision.reason ?: "RICAL provider is unavailable",
                )
            )
            RicalEvaluationState.INVALID -> RicalAttempt(
                fallback = RicalFallback(
                    MobileWalletProximityRicalState.Invalid,
                    result.decision.reason ?: "RICAL provider data is invalid",
                )
            )
            RicalEvaluationState.NO_MATCHING_AUTHORITY -> RicalAttempt(
                fallback = RicalFallback(
                    MobileWalletProximityRicalState.NoMatchingAuthority,
                    result.decision.reason ?: "RICAL has no matching reader authority",
                )
            )
        }
    }

    private fun MobileWalletProximityRicalConfiguration.signatureValidator(
        evaluatedAt: Instant,
    ): RicalSignatureValidator {
        val x509 = X509RicalSignatureValidator(acceptedSignerCertificatePolicyOids) { evaluatedAt }
        return RicalSignatureValidator { signed, roots ->
            if (!x509.validate(signed, roots)) {
                false
            } else {
                when (val policy = signerRevocationPolicy) {
                    MobileWalletProximityRicalSignerRevocationPolicy.NotChecked -> true
                    is MobileWalletProximityRicalSignerRevocationPolicy.Check -> try {
                        policy.evaluator.evaluate(
                            MobileWalletProximityRicalSignerEvidence(
                                providerId = providerId,
                                certificateChainDerBase64Url = signed.signerChainDer.map {
                                    it.copy().encodeTrustBase64Url()
                                },
                            )
                        ) == MobileWalletProximityCertificateRevocationResult.Good
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        false
                    }
                }
            }
        }
    }

    private suspend fun decisionForValidatedPath(
        evidence: MobileWalletProximityReaderEvidence,
        displayName: String,
        rical: MobileWalletProximityRicalState,
        establishesTrust: Boolean,
    ): MobileWalletProximityReaderTrustDecision = when (val revocation = evaluateRevocation(evidence)) {
        is EvaluatedRevocation.Good -> MobileWalletProximityReaderTrustDecision(
            state = if (establishesTrust) MobileWalletProximityReaderTrustState.Trusted
                else MobileWalletProximityReaderTrustState.ValidButUntrusted,
            certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
            revocation = revocation.state,
            rical = rical,
            displayName = displayName,
            reason = if (establishesTrust) null
                else "RICAL evidence is valid but the active policy does not establish reader trust",
        )
        is EvaluatedRevocation.Revoked -> MobileWalletProximityReaderTrustDecision(
            state = MobileWalletProximityReaderTrustState.Revoked,
            certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
            revocation = MobileWalletProximityReaderRevocationState.Revoked,
            rical = rical,
            displayName = displayName,
            reason = revocation.reason ?: "Reader authentication certificate is revoked",
        )
        is EvaluatedRevocation.Indeterminate -> MobileWalletProximityReaderTrustDecision(
            state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
            certificatePath = MobileWalletProximityReaderCertificatePathState.Valid,
            revocation = MobileWalletProximityReaderRevocationState.Indeterminate,
            rical = rical,
            displayName = displayName,
            reason = revocation.reason,
        )
    }

    private suspend fun evaluateRevocation(
        evidence: MobileWalletProximityReaderEvidence,
    ): EvaluatedRevocation = when (val policy = configuration.revocationPolicy) {
        MobileWalletProximityReaderRevocationPolicy.NotChecked ->
            EvaluatedRevocation.Good(MobileWalletProximityReaderRevocationState.NotChecked)
        is MobileWalletProximityReaderRevocationPolicy.Check -> try {
            when (val result = policy.evaluator.evaluate(evidence)) {
                MobileWalletProximityCertificateRevocationResult.Good ->
                    EvaluatedRevocation.Good(MobileWalletProximityReaderRevocationState.Good)
                is MobileWalletProximityCertificateRevocationResult.Revoked ->
                    EvaluatedRevocation.Revoked(result.reason)
                is MobileWalletProximityCertificateRevocationResult.Indeterminate ->
                    EvaluatedRevocation.Indeterminate(result.reason)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            EvaluatedRevocation.Indeterminate("Reader certificate revocation status is unavailable")
        }
    }

    private sealed interface EvaluatedRevocation {
        data class Good(val state: MobileWalletProximityReaderRevocationState) : EvaluatedRevocation
        data class Revoked(val reason: String?) : EvaluatedRevocation
        data class Indeterminate(val reason: String) : EvaluatedRevocation
    }

    private sealed interface RicalMatch {
        val displayName: String?

        data class Valid(
            override val displayName: String?,
            val establishesTrust: Boolean,
        ) : RicalMatch

        data class Revoked(
            override val displayName: String?,
            val reason: String?,
        ) : RicalMatch
    }
    private data class RicalFallback(val state: MobileWalletProximityRicalState, val reason: String)
    private data class RicalAttempt(val matched: RicalMatch? = null, val fallback: RicalFallback? = null)

    private fun RicalFallback?.prefer(candidate: RicalFallback?): RicalFallback? {
        candidate ?: return this
        this ?: return candidate
        fun MobileWalletProximityRicalState.priority(): Int = when (this) {
            MobileWalletProximityRicalState.Invalid -> 3
            MobileWalletProximityRicalState.Unavailable -> 2
            MobileWalletProximityRicalState.NoMatchingAuthority -> 1
            MobileWalletProximityRicalState.NotEvaluated, MobileWalletProximityRicalState.Matched -> 0
        }
        return if (candidate.state.priority() > state.priority()) candidate else this
    }

    private fun invalidPathDecision(): MobileWalletProximityReaderTrustDecision =
        MobileWalletProximityReaderTrustDecision(
            state = MobileWalletProximityReaderTrustState.ValidButUntrusted,
            certificatePath = MobileWalletProximityReaderCertificatePathState.Invalid,
            reason = "Reader authentication certificate path or profile is invalid",
        )
}

private fun MobileWalletProximityReaderEvidence.toRicalEvidence(): ReaderAuthenticationEvidence =
    ReaderAuthenticationEvidence(
        scope = when (scope) {
            MobileWalletProximityReaderAuthenticationScope.Document ->
                id.walt.mdoc.proximity.ReaderAuthenticationScope.DOCUMENT
            MobileWalletProximityReaderAuthenticationScope.WholeRequest ->
                id.walt.mdoc.proximity.ReaderAuthenticationScope.WHOLE_REQUEST
        },
        documentRequestIndex = documentRequestIndex,
        authenticationIndex = authenticationIndex,
        certificateChainDer = certificateChainDerBase64Url.map {
            ImmutableBytes.of(it.decodeTrustBase64Url())
        },
    )

private fun RicalTrustConstraint.toPublic(): MobileWalletProximityRicalTrustConstraint =
    MobileWalletProximityRicalTrustConstraint(
        valuesCborBase64Url = values.mapValues { (_, value) -> value.toTrustBase64Url() }
    )

private fun CborElement.toTrustBase64Url(): String =
    coseCompliantCbor.encodeToByteArray(CborElement.serializer(), this).encodeTrustBase64Url()

private fun String.trustCertificateDer(): CertificateDer = CertificateDer(decodeTrustBase64Url()).also {
    X509CertificateUtil.parseCertificateDerEncoded(it.bytes)
}

@OptIn(ExperimentalEncodingApi::class)
private fun String.decodeTrustBase64Url(): ByteArray =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).decode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeTrustBase64Url(): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(this)

@OptIn(ExperimentalEncodingApi::class)
private fun String.isTrustBase64Url(): Boolean =
    isNotBlank() && !contains('=') && runCatching { decodeTrustBase64Url().isNotEmpty() }.getOrDefault(false)
