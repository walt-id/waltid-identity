package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import kotlinx.serialization.Serializable

/**
 * One point of the OpenID4VP 1.0 wallet conformance matrix.
 *
 * Mirrors [id.walt.openid4vp.conformance.testplans.plans.vp.verifier.VerifierVariantMatrix]. Before
 * this, only the HAIP wallet plan was driven; `oid4vp-1final-wallet-test-plan` was never requested at
 * all, leaving every non-HAIP client-identifier prefix and request method untested.
 */
@Serializable
data class WalletVariant(
    val credentialFormat: String,
    val clientIdPrefix: String,
    val requestMethod: String,
    val responseMode: String,
    val vpProfile: String,
) {
    val isHaip: Boolean get() = vpProfile == "haip"

    val isMdoc: Boolean get() = credentialFormat == "iso_mdl"

    /** `direct_post.jwt` and `dc_api.jwt` are the encrypted response modes. */
    val encryptedResponse: Boolean get() = responseMode.endsWith(".jwt")

    /** Only a request object can be signed; the other methods deliver parameters in the clear. */
    val signedRequest: Boolean
        get() = requestMethod == "request_uri_signed" || requestMethod == "request_uri_multisigned"

    val planName: String
        get() = if (isHaip) "oid4vp-1final-wallet-haip-test-plan" else "oid4vp-1final-wallet-test-plan"

    val id: String
        get() = listOf(
            "wallet",
            credentialFormat.toIdPart(),
            clientIdPrefix.toIdPart(),
            requestMethod.toIdPart(),
            responseMode.toIdPart(),
            vpProfile.toIdPart(),
        ).joinToString("-")

    val description: String
        get() = "VP Wallet - $credentialFormat + $clientIdPrefix + $requestMethod + $responseMode + $vpProfile"

    /**
     * Variant sent when creating the test plan.
     *
     * The HAIP plan fixes `client_id_prefix`, `request_method` and `vp_profile` itself and rejects
     * them being restated, so only the two free axes go there.
     */
    fun testPlanCreationVariant(): Map<String, String> = buildMap {
        put("credential_format", credentialFormat)
        put("response_mode", responseMode)
        if (!isHaip) {
            put("client_id_prefix", clientIdPrefix)
            put("request_method", requestMethod)
            put("vp_profile", vpProfile)
        }
    }

    /**
     * All five variant axes, under the suite's parameter names.
     *
     * Unlike [testPlanCreationVariant] this always describes the variant in full: the axes the HAIP
     * plan fixes itself are absent from the creation request but still describe what runs, and
     * judging module applicability against a partial selection would silently misclassify every HAIP
     * point.
     */
    fun axisValues(): Map<String, String> = mapOf(
        "credential_format" to credentialFormat,
        "client_id_prefix" to clientIdPrefix,
        "request_method" to requestMethod,
        "response_mode" to responseMode,
        "vp_profile" to vpProfile,
    )

    private fun String.toIdPart(): String = when (this) {
        "sd_jwt_vc" -> "sdjwt"
        "iso_mdl" -> "mdl"
        "request_uri_signed" -> "requrisigned"
        "request_uri_unsigned" -> "requriunsigned"
        "url_query" -> "urlquery"
        "direct_post.jwt" -> "directpostjwt"
        "direct_post" -> "directpost"
        else -> replace("_", "").replace(".", "")
    }
}

object WalletVariantMatrix {

    private val credentialFormats = listOf("sd_jwt_vc", "iso_mdl")
    private val responseModes = listOf("direct_post", "direct_post.jwt")

    /**
     * Client identifier prefixes driven against Wallet2.
     *
     * Excluded, with reasons:
     * - `web-origin`: absent from `id.walt.openid4vp.clientidprefix.ClientIdPrefix`, so Wallet2
     *   cannot authenticate it at all. It is also DC-API-only in practice.
     * - `decentralized_identifier`: needs the suite to sign request objects with a key resolvable
     *   from a DID it controls, and no such fixture exists yet.
     * - `pre_registered`: needs the verifier's metadata in
     *   `OSSWallet2ServiceConfig.clientIdTrust.preRegisteredClients`, which the conformance wallet
     *   does not configure yet.
     *
     * Both remaining families are additive work rather than blockers - see the KDoc above.
     */
    private val clientIdPrefixes = listOf("redirect_uri", "x509_san_dns", "x509_hash")

    /**
     * Request methods driven against Wallet2.
     *
     * `request_uri_unsigned` is included even though the suite serialises those Request Objects with a
     * bare `PlainHeader` - the observed header is exactly `{"alg":"none"}`, see
     * `AbstractSignClaimsWithNullAlgorithm` - and so omits the `typ` that OpenID4VP 1.0 Section 5
     * requires. The suite is the arbiter of interoperability, so Wallet2 tolerates an absent `typ` on
     * unsigned Request Objects rather than this matrix dropping the request method; see
     * `AuthorizationRequestResolver.requireRequestObjectType`.
     *
     * `request_uri_multisigned` is genuinely unreachable: the suite declares it DC-API-only (OID4VP
     * Appendix A.3.2), and this harness speaks HTTP to the wallet.
     */
    private val requestMethods = listOf("url_query", "request_uri_unsigned", "request_uri_signed")


    fun all(): List<WalletVariant> = plainVp() + haip()

    fun plainVp(): List<WalletVariant> = buildList {
        credentialFormats.forEach { credentialFormat ->
            clientIdPrefixes.forEach { clientIdPrefix ->
                requestMethods.forEach { requestMethod ->
                    responseModes.forEach { responseMode ->
                        val variant = WalletVariant(
                            credentialFormat = credentialFormat,
                            clientIdPrefix = clientIdPrefix,
                            requestMethod = requestMethod,
                            responseMode = responseMode,
                            vpProfile = "plain_vp",
                        )
                        if (isApplicable(variant)) add(variant)
                    }
                }
            }
        }
    }

    /** HAIP fixes x509_hash + a signed request + an encrypted response. */
    fun haip(): List<WalletVariant> = credentialFormats.map { credentialFormat ->
        WalletVariant(
            credentialFormat = credentialFormat,
            clientIdPrefix = "x509_hash",
            requestMethod = "request_uri_signed",
            responseMode = "direct_post.jwt",
            vpProfile = "haip",
        )
    }

    /**
     * Combinations that can actually be driven.
     *
     * This decides which *plans* are requested. Which *modules* of a requested plan actually apply is
     * a separate question the suite answers per module - see [WalletModuleApplicability].
     *
     * The first group is transcribed from `AbstractVP1FinalWalletTest`'s `@VariantNotApplicableWhen`
     * annotations, so the suite is never asked for a plan it would reject with
     * "No test modules ... applicable". The second group is what Wallet2 and this harness can drive.
     *
     * `dc_api` / `dc_api.jwt` never appear in [responseModes]: they need the browser Digital
     * Credentials API, whereas this harness talks HTTP to the wallet. `request_uri_multisigned` is
     * consequently unreachable too - the suite declares it DC-API-only (OID4VP Appendix A.3.2).
     */
    fun isApplicable(variant: WalletVariant): Boolean = when {
        // --- Suite rules ---

        // HAIP mandates an encrypted response.
        variant.isHaip && !variant.encryptedResponse -> false

        // A redirect_uri client identifier carries no key, so it cannot authenticate a signed request.
        variant.clientIdPrefix == "redirect_uri" && variant.signedRequest -> false

        // --- Wallet2 / harness capability ---

        // x509 prefixes authenticate the verifier through the request object's signature and x5c
        // chain (OID4VP 1.0 §5.9.3-3.5.1, -3.6.1). An unsigned request carries neither, so Wallet2
        // rejects it - correctly - and the module cannot pass as a positive test.
        variant.clientIdPrefix in setOf("x509_san_dns", "x509_hash") && !variant.signedRequest -> false

        else -> true
    }
}
