package id.walt.issuer2.config

/**
 * The trust registry resolver that decides `x509-trust-registry` client and key attestations.
 *
 * @property resolveCertificateChainUrl the full URL of a `resolve/certificate-chain` endpoint, for example an
 *   Enterprise `{base}/v1/{target}/trust-registry-api/resolve/certificate-chain`. A resolver on a loopback or
 *   private address must be allowed in `network-fetch-guard.conf`.
 * @property headers sent with every request, for example `Authorization = "Bearer ..."`.
 */
data class TrustDecisionResolverConfig(
    val resolveCertificateChainUrl: String,
    val headers: Map<String, String> = emptyMap(),
) {
    /** Header values can hold credentials, and configurations are logged. */
    override fun toString(): String =
        "TrustDecisionResolverConfig(resolveCertificateChainUrl=$resolveCertificateChainUrl, headers=${headers.keys})"
}
