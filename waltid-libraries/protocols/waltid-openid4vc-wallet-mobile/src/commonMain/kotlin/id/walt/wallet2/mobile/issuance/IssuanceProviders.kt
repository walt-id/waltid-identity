package id.walt.wallet2.mobile.issuance

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.wallet2.walletprovider.WalletProviderHttpClient
import id.walt.wallet2.walletprovider.walletProviderVerificationKey
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.HttpWalletAttestationProvider
import id.waltid.openid4vci.wallet.attestation.WalletAttestationProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal class IssuanceProviders(
    walletProviderConfig: WalletProviderConfig?,
    attestationConfig: WalletAttestationConfig?,
    attestationProvider: WalletAttestationProvider?,
    providedHttpClient: HttpClient?,
) {
    private val ownedProviderHttpClient = if (providedHttpClient == null && (walletProviderConfig != null || attestationConfig != null)) {
        HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    } else null

    val walletProviderClient: WalletProviderHttpClient? = walletProviderConfig?.let { config ->
        WalletProviderHttpClient(
            baseUrl = config.baseUrl,
            servicePath = config.servicePath,
            bearerToken = config.bearerToken,
            hostHeader = config.hostHeader,
            composition = config.composition.wireValue,
            httpClient = providedHttpClient ?: requireNotNull(ownedProviderHttpClient),
            verificationKeyFromJwt = { jwt ->
                walletProviderVerificationKey(jwt) { pem ->
                    X509CertificateUtil.parseCertificatePem(pem)
                        .restoreSubjectPublicKey(CryptoRuntime(defaultSoftwareKeyProviders()))
                }
            },
        )
    }

    val walletProviderComposition = walletProviderConfig?.composition

    val attestationAssembler: ClientAttestationAssembler? = when {
        walletProviderClient != null && walletProviderComposition != WalletProviderConfig.Composition.Key ->
            ClientAttestationAssembler(walletProviderClient)
        attestationProvider != null -> ClientAttestationAssembler(attestationProvider)
        attestationConfig != null -> ClientAttestationAssembler(
            HttpWalletAttestationProvider(
                baseUrl = attestationConfig.baseUrl,
                attesterPath = attestationConfig.attesterPath,
                bearerToken = attestationConfig.bearerToken,
                hostHeader = attestationConfig.hostHeader,
                httpClient = providedHttpClient ?: requireNotNull(ownedProviderHttpClient),
            )
        )
        else -> null
    }

    fun close() { ownedProviderHttpClient?.close() }
}
