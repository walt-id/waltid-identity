package id.walt.wallet2.mobile.proximity

/** Scope covered by one reader-authentication statement. */
public sealed interface ProximityReaderAuthenticationScope {
    /**
     * A verified or attempted statement for exactly one document request.
     * @property index Nonnegative zero-based document request index.
     */
    public data class Document(public val index: Int) : ProximityReaderAuthenticationScope {
        init { require(index >= 0) }
    }

    /** A statement covering the complete device request. */
    public data object WholeRequest : ProximityReaderAuthenticationScope
}

/** Cryptographic validity of reader authentication, kept separate from trust. */
public enum class ProximityReaderAuthenticationValidity {
    Absent,
    Malformed,
    Invalid,
    Valid,
}

/** Scope-aware summary for the reviewed documents; raw statement evidence remains available. */
public enum class ProximityReaderAuthenticationSummary {
    /** No reader-authentication statement was supplied. */
    Absent,
    /** At least one supplied statement could not be parsed. */
    Malformed,
    /** At least one supplied statement failed cryptographic verification. */
    Invalid,
    /** At least one verified reader statement was revoked. */
    Revoked,
    /** Some reviewed documents lack valid authentication coverage. */
    Partial,
    /** Every document is authenticated, but trusted coverage is incomplete. */
    ValidButUntrusted,
    /** Every document is covered by trusted document or whole-request authentication. */
    Trusted,
}

/** Trust outcome after valid reader authentication. */
public enum class ProximityReaderTrustState {
    NotEvaluated,
    ValidButUntrusted,
    Revoked,
    Trusted,
}

/** Result of validating the reader certificate path against explicitly configured trust material. */
public enum class ProximityReaderCertificatePathState {
    NotEvaluated,
    UnknownAuthority,
    Invalid,
    Valid,
}

/** Reader-certificate revocation fact, kept independent from path validity and product trust. */
public enum class ProximityReaderRevocationState {
    NotChecked,
    Good,
    Revoked,
    Indeterminate,
}

/** Optional RICAL evidence fact. A match never establishes product trust by itself. */
public enum class ProximityRicalState {
    NotEvaluated,
    Unavailable,
    Invalid,
    NoMatchingAuthority,
    Matched,
}

/**
 * Exact verified reader evidence supplied to an application-owned trust policy.
 *
 * @property scope Request scope covered by the verified statement.
 */
public data class ProximityReaderEvidence(
    public val scope: ProximityReaderAuthenticationScope,
    /** DER certificates in leaf-first order, encoded as unpadded Base64URL. */
    public val certificateChainDerBase64Url: List<String>,
    /** Zero-based statement index within the authentication scope. */
    public val authenticationIndex: Int = 0,
) {
    init {
        require(authenticationIndex >= 0)
        require(certificateChainDerBase64Url.isNotEmpty()) {
            "Verified reader evidence requires a certificate chain"
        }
        require(certificateChainDerBase64Url.all(String::isNonEmptyBase64Url)) {
            "Reader certificates must be non-empty unpadded Base64URL values"
        }
    }
}

/**
 * Trust decision supplied by the hosting wallet application.
 *
 * @property state Product trust outcome.
 * @property certificatePath Result of path validation against configured trust material.
 * @property revocation Independently evaluated certificate revocation fact.
 * @property rical Optional RICAL evidence fact.
 * @property displayName Display-safe reader name established by the trust policy.
 * @property reason Display-safe explanation of the decision.
 */
public data class ProximityReaderTrustDecision(
    public val state: ProximityReaderTrustState,
    public val certificatePath: ProximityReaderCertificatePathState =
        ProximityReaderCertificatePathState.NotEvaluated,
    public val revocation: ProximityReaderRevocationState =
        ProximityReaderRevocationState.NotChecked,
    public val rical: ProximityRicalState = ProximityRicalState.NotEvaluated,
    public val displayName: String? = null,
    public val reason: String? = null,
) {
    init {
        require(displayName == null || displayName.isNotBlank())
        require(reason == null || reason.isNotBlank())
        validateReaderTrustFacts(state, certificatePath, revocation, rical)
    }
}

/** Explicit trust boundary for verified reader certificate evidence. */
public fun interface ProximityReaderTrustEvaluator {
    /** Evaluates product trust from already verified reader certificate [evidence]. */
    public suspend fun evaluate(
        evidence: ProximityReaderEvidence,
    ): ProximityReaderTrustDecision
}

/**
 * Display-safe reader authentication and trust fact for holder consent.
 *
 * @property scope Request scope covered by this authentication statement.
 * @property validity Cryptographic validity, independent of product trust.
 * @property trust Product trust outcome after valid authentication.
 * @property certificatePath Certificate-path validation result.
 * @property revocation Certificate revocation fact.
 * @property rical Optional RICAL evidence fact.
 * @property displayName Display-safe authenticated reader name, when established.
 * @property reason Display-safe explanation of validity or trust.
 */
public data class ProximityReaderAuthentication(
    public val scope: ProximityReaderAuthenticationScope,
    /** Only valid authentication carries evaluated trust facts. */
    public val outcome: ProximityReaderAuthenticationOutcome,
    /** Zero-based statement index within the authentication scope. */
    public val authenticationIndex: Int = 0,
) {
    init { require(authenticationIndex >= 0) }

    /** Display validity derived from the outcome. */
    public val validity: ProximityReaderAuthenticationValidity
        get() = when (outcome) {
            ProximityReaderAuthenticationOutcome.Absent -> ProximityReaderAuthenticationValidity.Absent
            is ProximityReaderAuthenticationOutcome.Malformed -> ProximityReaderAuthenticationValidity.Malformed
            is ProximityReaderAuthenticationOutcome.Invalid -> ProximityReaderAuthenticationValidity.Invalid
            is ProximityReaderAuthenticationOutcome.Valid -> ProximityReaderAuthenticationValidity.Valid
        }

    private val decision: ProximityReaderTrustDecision?
        get() = (outcome as? ProximityReaderAuthenticationOutcome.Valid)?.trust

    /** Display trust state; authentication failure has no evaluated trust decision. */
    public val trust: ProximityReaderTrustState
        get() = decision?.state ?: ProximityReaderTrustState.NotEvaluated
    /** Independently evaluated certificate-path fact. */
    public val certificatePath: ProximityReaderCertificatePathState
        get() = decision?.certificatePath ?: ProximityReaderCertificatePathState.NotEvaluated
    /** Independently evaluated revocation fact. */
    public val revocation: ProximityReaderRevocationState
        get() = decision?.revocation ?: ProximityReaderRevocationState.NotChecked
    /** Independently evaluated RICAL fact. */
    public val rical: ProximityRicalState
        get() = decision?.rical ?: ProximityRicalState.NotEvaluated
    /** Reader name established by the trust policy. */
    public val displayName: String? get() = decision?.displayName
    /** Display-safe authentication or trust explanation. */
    public val reason: String?
        get() = when (outcome) {
            ProximityReaderAuthenticationOutcome.Absent -> null
            is ProximityReaderAuthenticationOutcome.Malformed -> outcome.reason
            is ProximityReaderAuthenticationOutcome.Invalid -> outcome.reason
            is ProximityReaderAuthenticationOutcome.Valid -> outcome.trust.reason
        }
}

/** Authentication result whose trust payload exists only after successful verification. */
public sealed interface ProximityReaderAuthenticationOutcome {
    /** No authentication statement was supplied. */
    public data object Absent : ProximityReaderAuthenticationOutcome
    /**
     * The statement could not be parsed.
     * @property reason Display-safe parsing failure.
     */
    public data class Malformed(public val reason: String) : ProximityReaderAuthenticationOutcome {
        init { require(reason.isNotBlank()) }
    }

    /**
     * Cryptographic authentication failed.
     * @property reason Display-safe verification failure.
     */
    public data class Invalid(public val reason: String) : ProximityReaderAuthenticationOutcome {
        init { require(reason.isNotBlank()) }
    }

    /**
     * Verified authentication with independent application trust facts.
     * @property trust Evaluated application trust, or the explicit not-evaluated default.
     */
    public data class Valid(public val trust: ProximityReaderTrustDecision) :
        ProximityReaderAuthenticationOutcome
}

/** Document index derived from the scope, without an independent nullable constructor argument. */
internal val ProximityReaderAuthenticationScope.documentRequestIndex: Int?
    get() = (this as? ProximityReaderAuthenticationScope.Document)?.index

/** Default policy: validity is reported, but no reader certificate becomes trusted implicitly. */
public object UnconfiguredProximityReaderTrustEvaluator :
    ProximityReaderTrustEvaluator {
    /** Returns valid-but-untrusted because no application trust policy is configured. */
    override suspend fun evaluate(
        evidence: ProximityReaderEvidence,
    ): ProximityReaderTrustDecision = ProximityReaderTrustDecision(
        state = ProximityReaderTrustState.ValidButUntrusted,
        reason = "No reader trust policy is configured",
    )
}

private fun validateReaderTrustFacts(
    state: ProximityReaderTrustState,
    certificatePath: ProximityReaderCertificatePathState,
    revocation: ProximityReaderRevocationState,
    rical: ProximityRicalState,
) {
    require(state != ProximityReaderTrustState.NotEvaluated) {
        "Valid reader authentication requires an evaluated trust state"
    }
    require(state != ProximityReaderTrustState.Revoked ||
        revocation == ProximityReaderRevocationState.Revoked) {
        "A revoked trust decision requires a revoked certificate result"
    }
    require(revocation != ProximityReaderRevocationState.Revoked ||
        state == ProximityReaderTrustState.Revoked) {
        "A revoked certificate result requires a revoked trust decision"
    }
    require(state != ProximityReaderTrustState.Trusted ||
        certificatePath == ProximityReaderCertificatePathState.Valid) {
        "A trusted reader requires a valid certificate path"
    }
    require(state != ProximityReaderTrustState.Revoked ||
        certificatePath == ProximityReaderCertificatePathState.Valid) {
        "A revoked reader requires a valid certificate path"
    }
    require(certificatePath != ProximityReaderCertificatePathState.UnknownAuthority ||
        state == ProximityReaderTrustState.ValidButUntrusted) {
        "An unknown reader authority must remain valid but untrusted"
    }
    require(certificatePath != ProximityReaderCertificatePathState.Invalid ||
        state == ProximityReaderTrustState.ValidButUntrusted) {
        "An invalid reader path must remain valid but untrusted"
    }
    require(certificatePath != ProximityReaderCertificatePathState.Invalid ||
        revocation == ProximityReaderRevocationState.NotChecked) {
        "Revocation cannot be evaluated for an invalid reader path"
    }
    require(state != ProximityReaderTrustState.Trusted ||
        revocation != ProximityReaderRevocationState.Indeterminate) {
        "A reader with indeterminate revocation status cannot be trusted"
    }
    require(rical != ProximityRicalState.Matched ||
        certificatePath == ProximityReaderCertificatePathState.Valid) {
        "A matching RICAL authority requires a valid reader path"
    }
}
