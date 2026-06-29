package id.walt.openid4vp.conformance.config

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
     */
    const val CONFORMANCE_HOST = "localhost.emobix.co.uk"
    
    /**
     * Conformance suite HTTPS port.
     * Local: 8443
     * Cloud: 443
     */
    const val CONFORMANCE_PORT = 8443
    
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
     */
    const val WALLET_API_URL = "http://127.0.0.1:7005"
    
    /**
     * Wallet conformance adapter port.
     * The adapter bridges conformance suite -> wallet API.
     */
    const val WALLET_ADAPTER_PORT = 7006
    
    /**
     * Wallet adapter authorization endpoint URL.
     */
    val WALLET_ADAPTER_URL: String
        get() = "http://127.0.0.1:$WALLET_ADAPTER_PORT/openid4vp/authorize"
    
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
    const val ISSUER_LOCAL_PORT = 7002
    
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
