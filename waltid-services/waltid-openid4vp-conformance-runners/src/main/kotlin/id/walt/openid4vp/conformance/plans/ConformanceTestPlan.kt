package id.walt.openid4vp.conformance.plans

import kotlinx.serialization.json.JsonObject

/**
 * Base interface for all conformance test plans.
 *
 * A test plan represents a specific combination of protocol variants
 * that should be tested against the OpenID conformance suite.
 */
interface ConformanceTestPlan {
    /**
     * Human-readable description of this test plan.
     */
    val description: String

    /**
     * Conformance suite test plan name.
     * Example: "oid4vp-1final-verifier-test-plan"
     */
    val planName: String

    /**
     * Test plan variant parameters.
     * These define the specific protocol options being tested.
     */
    val variant: Map<String, String>

    /**
     * Test plan configuration JSON for conformance suite API.
     */
    val configuration: JsonObject

    /**
     * Whether this is an optional test plan.
     */
    val optional: Boolean
        get() = false
}

/**
 * Test plan for verifier conformance testing.
 *
 * In verifier tests, the conformance suite acts as a wallet
 * and sends VP responses to the verifier under test.
 */
interface VerifierTestPlan : ConformanceTestPlan {
    /**
     * Verifier URL prefix where the embedded verifier is reachable.
     * Must be accessible from the conformance suite (Docker container).
     */
    val verifierUrlPrefix: String

    /**
     * Expected outcome per module name.
     * Maps module name -> whether verifier should accept or reject.
     */
    val moduleOutcomes: Map<String, ModuleOutcome>

    /**
     * Credential format being tested.
     */
    val credentialFormat: String
        get() = variant["credential_format"] ?: "sd_jwt_vc"

    /**
     * Client ID prefix scheme.
     */
    val clientIdPrefix: String
        get() = variant["client_id_prefix"] ?: "x509_san_dns"

    /**
     * Request method (signed/unsigned).
     */
    val requestMethod: String
        get() = variant["request_method"] ?: "request_uri_signed"

    /**
     * Response mode (direct_post, direct_post.jwt).
     */
    val responseMode: String
        get() = variant["response_mode"] ?: "direct_post"

    /**
     * VP profile (plain_vp, haip).
     */
    val vpProfile: String
        get() = variant["vp_profile"] ?: "plain_vp"
}

/**
 * Test plan for wallet conformance testing.
 *
 * In wallet tests, the conformance suite acts as a verifier
 * and sends authorization requests to the wallet under test.
 */
interface WalletTestPlan : ConformanceTestPlan {
    /**
     * Wallet authorization endpoint URL.
     * This is where the conformance suite sends requests.
     */
    val walletAuthorizationEndpoint: String

    /**
     * Wallet API base URL for programmatic operations.
     */
    val walletApiUrl: String
        get() = "http://127.0.0.1:7005"

    /**
     * Whether this test expects the wallet to reject requests.
     */
    val expectRejection: Boolean
        get() = false

    /**
     * Credential format being tested.
     */
    val credentialFormat: String
        get() = variant["credential_format"] ?: "sd_jwt_vc"

    /**
     * Whether this is a HAIP test plan.
     */
    val isHAIP: Boolean
        get() = variant["vp_profile"] == "haip" || planName.contains("haip")

    /**
     * Whether encrypted response is required.
     */
    val requiresEncryptedResponse: Boolean
        get() = variant["response_mode"] == "direct_post.jwt" || isHAIP

    /**
     * Whether signed request is required.
     */
    val requiresSignedRequest: Boolean
        get() = variant["request_method"] == "request_uri_signed" || isHAIP
}

/**
 * Test plan for issuer conformance testing.
 *
 * In issuer tests, the conformance suite acts as a wallet
 * and requests credential issuance from the issuer under test.
 */
interface IssuerTestPlan : ConformanceTestPlan {
    /**
     * Issuer URL prefix where the embedded issuer is reachable.
     */
    val issuerUrlPrefix: String

    /**
     * Credential configuration ID to test.
     */
    val credentialConfigurationId: String?
        get() = null
}

/**
 * Expected outcome for a test module.
 */
enum class ModuleOutcome {
    /**
     * Verifier/Wallet must accept - test passes if session ends SUCCESSFUL.
     */
    ACCEPT,

    /**
     * Verifier/Wallet must reject - test passes if session ends with error.
     */
    REJECT,

    /**
     * Test may be skipped by conformance suite.
     * If not skipped, treated as ACCEPT.
     */
    ACCEPT_OR_SKIP,

    /**
     * Test outcome not yet determined - placeholder for new modules.
     */
    UNKNOWN
}
