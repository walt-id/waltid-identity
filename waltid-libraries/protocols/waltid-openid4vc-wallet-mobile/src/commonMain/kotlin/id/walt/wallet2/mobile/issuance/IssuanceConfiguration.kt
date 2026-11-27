package id.walt.wallet2.mobile.issuance

import id.walt.wallet2.issuance.CredentialReceiptTrust
import id.walt.crypto.utils.requireEndpointHost
import id.walt.crypto.utils.requireHttpEndpoint
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import io.ktor.http.Url

/**
 * Issuer trust and attestation services.
 * @property attestation Optional client-attestation service for issuers that require it.
 * @property walletProvider Enterprise service supplying wallet-instance or holder-key attestations.
 * @property metadataTrustResolver Verifier for signed Credential Issuer Metadata. When absent, the
 * wallet neither requests nor accepts signed metadata.
 */
public data class IssuanceConfiguration(
    public val attestation: WalletAttestationConfig? = null,
    public val walletProvider: WalletProviderConfig? = null,
    public val metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    /** Optional issuer authentication before credential storage and OS indexing. */
    public val receiptTrust: CredentialReceiptTrust? = null,
) {
    init {
        attestation?.let { requireServiceEndpoint(it.baseUrl, it.attesterPath, it.hostHeader) }
        walletProvider?.let { requireServiceEndpoint(it.baseUrl, it.servicePath, it.hostHeader) }
    }
}

private fun requireServiceEndpoint(baseUrl: String, path: String, hostHeader: String) {
    requireHttpEndpoint(baseUrl, "Attestation service")
    require(baseUrl.none(Char::isWhitespace)) { "Attestation service URL cannot contain whitespace" }
    require(runCatching { Url(baseUrl).port in 1..65535 }.getOrDefault(false)) { "Invalid attestation service URL or port" }
    require(path.trim('/').isNotBlank() && path.none { it.isWhitespace() || it in "?#" } &&
        "://" !in path && path.split('/').none { it == "." || it == ".." }) { "Invalid attestation service path" }
    if (hostHeader.isNotEmpty()) requireEndpointHost(hostHeader, "Attestation service host override")
}
