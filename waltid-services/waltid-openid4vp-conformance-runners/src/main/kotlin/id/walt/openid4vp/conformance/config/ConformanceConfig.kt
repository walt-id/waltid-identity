package id.walt.openid4vp.conformance.config

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Central configuration for conformance test infrastructure.
 *
 * All test classes should use these shared settings rather than
 * hardcoding values, making it easy to switch between local and
 * cloud conformance suite deployments.
 */
object ConformanceConfig {

    // ================================
    // Conformance Suite Settings
    // ================================

    /**
     * Conformance suite hostname.
     * Local: localhost.emobix.co.uk (requires /etc/hosts entry)
     * Cloud: conformance.waltid.cloud
     *
     * Override with `CONFORMANCE_HOST`.
     */
    val CONFORMANCE_HOST: String
        get() = env("CONFORMANCE_HOST") ?: "localhost.emobix.co.uk"

    /**
     * Conformance suite HTTPS port.
     * Local: 8443
     * Cloud: 443
     *
     * Override with `CONFORMANCE_PORT`.
     */
    val CONFORMANCE_PORT: Int
        get() = env("CONFORMANCE_PORT")?.toIntOrNull() ?: 8443

    // ================================
    // Verifier Settings
    // ================================

    /**
     * Local verifier host for embedded test server.
     */
    const val VERIFIER_LOCAL_HOST = "127.0.0.1"

    /**
     * Local verifier port for embedded test server.
     */
    const val VERIFIER_LOCAL_PORT = 7003

    /**
     * Verifier URL prefix placeholder.
     * Must be replaced with ngrok URL or similar for Docker-based conformance suite.
     * Format: "https://xyz.ngrok-free.app/verification-session"
     */
    const val VERIFIER_URL_PREFIX_PLACEHOLDER = "https://verifier2.localhost/verification-session"

    // ================================
    // Wallet Settings
    // ================================

    /**
     * Wallet API base URL for programmatic credential operations.
     *
     * NOTE: Use wallet-api2 (port 7005), not the old wallet-api (port 7005).
     * Start with: ./gradlew :waltid-services:waltid-wallet-api2:run
     */
    const val WALLET_API_URL = "http://127.0.0.1:7005"

    /**
     * OpenID4VP wallet conformance adapter port.
     * The adapter bridges conformance suite -> wallet API.
     */
    const val WALLET_ADAPTER_PORT = 7006

    /**
     * OpenID4VCI wallet conformance adapter port.
     */
    const val VCI_WALLET_ADAPTER_PORT = 7007

    /**
     * OAuth client identifier the VCI wallet plans register with the suite as `client.client_id`.
     *
     * Shared rather than repeated per plan because the suite cross-checks it: under
     * attestation-based client authentication the attestation's `sub` and the PoP's `iss` must both
     * equal this value, so a plan disagreeing with the wallet fails as a signature/subject mismatch
     * rather than as an obvious configuration error.
     */
    const val VCI_WALLET_CLIENT_ID = "wallet-conformance-test"

    /**
     * Path the VCI wallet adapter serves its test attester on.
     *
     * Shared because the wallet is configured with this URL before the adapter exists, so the two
     * would otherwise repeat the same path in different files.
     */
    const val VCI_WALLET_ATTESTATION_PATH = "/wallet-instance-attestation/jwk"

    /**
     * Host name the conformance suite has to use to reach an adapter started by the tests.
     *
     * The suite calls back into the machine running the tests. With the docker-compose topology
     * that is `host.docker.internal`; when the suite runs natively (the devenv setup) that name
     * does not resolve and loopback is the correct answer. Probing once and falling back keeps
     * both topologies working without configuration. Set `CONFORMANCE_ADAPTER_HOST` when neither
     * applies, e.g. for a remote suite that needs this machine's LAN address.
     */
    val ADAPTER_CALLBACK_HOST: String by lazy {
        System.getenv("CONFORMANCE_ADAPTER_HOST")?.ifBlank { null }
            ?: DOCKER_HOST_ALIAS.takeIf { isResolvable(it) }
            ?: "127.0.0.1"
    }

    private const val DOCKER_HOST_ALIAS = "host.docker.internal"

    private fun isResolvable(host: String): Boolean =
        try {
            InetAddress.getByName(host)
            true
        } catch (_: UnknownHostException) {
            false
        }

    /**
     * Wallet adapter authorization endpoint URL, reachable from the conformance suite.
     *
     * Override with `CONFORMANCE_VP_WALLET_ADAPTER_URL` when the adapter is exposed through a
     * public HTTPS tunnel. Cloudflare quick tunnels have no custom port, so `host:port` is not
     * enough for GitHub Actions.
     */
    val WALLET_ADAPTER_URL: String
        get() = walletAdapterAuthorizationUrl()

    /**
     * Authorization endpoint the OpenID4VP wallet plans register with the suite.
     */
    fun walletAdapterAuthorizationUrl(
        publicUrl: String? = env("CONFORMANCE_VP_WALLET_ADAPTER_URL"),
    ): String = publicUrl?.trim()?.takeIf { it.isNotEmpty() }
        ?: "http://$ADAPTER_CALLBACK_HOST:$WALLET_ADAPTER_PORT/openid4vp/authorize"

    /**
     * Credential-offer endpoint the OpenID4VCI wallet plans register with the suite.
     *
     * Local runs keep `http://$adapterHost:$adapterPort/credential-offer`. CI sets
     * `CONFORMANCE_VCI_WALLET_ADAPTER_BASE_URL` to the public HTTPS tunnel base so the cloud
     * suite can POST offers into the adapter. The test harness still delivers offers over
     * loopback separately.
     */
    fun vciSuiteCredentialOfferEndpoint(
        adapterHost: String,
        adapterPort: Int = VCI_WALLET_ADAPTER_PORT,
        publicBaseUrl: String? = env("CONFORMANCE_VCI_WALLET_ADAPTER_BASE_URL"),
    ): String {
        val base = publicBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        return if (base != null) {
            "$base/credential-offer"
        } else {
            "http://$adapterHost:$adapterPort/credential-offer"
        }
    }

    private fun env(name: String): String? =
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    // ================================
    // Issuer Settings
    // ================================

    /**
     * Local issuer host for embedded test server.
     */
    const val ISSUER_LOCAL_HOST = "127.0.0.1"

    /**
     * Local issuer port for embedded test server.
     */
    const val ISSUER_LOCAL_PORT = 7005

    /**
     * Issuer URL prefix placeholder.
     */
    const val ISSUER_URL_PREFIX_PLACEHOLDER = "https://issuer.localhost"

    // ================================
    // Timeouts
    // ================================

    /**
     * HTTP request timeout in milliseconds.
     */
    const val HTTP_REQUEST_TIMEOUT_MS = 60_000L

    /**
     * HTTP connect timeout in milliseconds.
     */
    const val HTTP_CONNECT_TIMEOUT_MS = 30_000L

    /**
     * Test execution timeout in minutes.
     */
    const val TEST_TIMEOUT_MINUTES = 10L

    // ================================
    // Helper Methods
    // ================================

    /**
     * Build conformance suite base URL.
     */
    fun conformanceBaseUrl(host: String = CONFORMANCE_HOST, port: Int = CONFORMANCE_PORT): String =
        "https://$host:$port"

    /**
     * Build verifier URL prefix for test configuration.
     */
    fun verifierUrlPrefix(ngrokUrl: String? = null): String =
        ngrokUrl?.let { "$it/verification-session" } ?: VERIFIER_URL_PREFIX_PLACEHOLDER

    /**
     * Check if URL is a placeholder that needs to be replaced.
     */
    fun isPlaceholderUrl(url: String): Boolean =
        url.contains(".localhost") || url.contains("xyz.ngrok")
}
