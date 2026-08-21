package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import id.walt.openid4vp.conformance.testplans.httpdata.AvailableTestModule

/**
 * Whether a test module the conformance suite published for a plan actually applies to the variant
 * that plan was created with.
 *
 * ### Why this is needed
 *
 * `POST /api/plan` is supposed to return only the modules applicable to the requested variant, and
 * mostly does. It has one hole, in `VariantService.TestModuleVariantInfo.isApplicableForVariant`:
 *
 * ```java
 * Set<?> effectiveAllowedValues = p.getAllowedValuesForVariant(variant);
 * // If all values are excluded, this parameter is not applicable - skip validation
 * if (effectiveAllowedValues.isEmpty()) {
 *     return true;
 * }
 * ```
 *
 * When a `@VariantNotApplicableWhen` rule removes *every* remaining value of a parameter, that means
 * the module cannot apply to the variant at all - but the suite reads it as "this parameter does not
 * constrain the module" and publishes the module anyway. It then fails on the very precondition the
 * annotations exist to prevent.
 *
 * For the wallet plan that affects two modules, both of which are unrunnable as published:
 * - `multisigned-one-invalid-signature`: applicable `request_method` is only
 *   `request_uri_multisigned`, which the suite itself restricts to the DC API response modes. With
 *   `direct_post` / `direct_post.jwt` nothing is left, so it is published for every variant and dies
 *   in `InvalidateFirstMultiSignedRequestObjectSignature` with "couldn't find required object in
 *   environment before evaluation: request_object_json".
 * - `negative-test-response-uri-not-client-id`: applicable `client_id_prefix` is `redirect_uri` or
 *   `web-origin`, both of which are excluded when the request is signed. It is nevertheless published
 *   for the `x509_*` prefixes.
 *
 * The HAIP plan does not suffer from this because it removes those modules from its own module list
 * by hand, each with a comment naming the annotation being worked around. The non-HAIP plan does not,
 * which is consistent with it being labelled "alpha tests (not currently part of certification
 * program)".
 *
 * ### Why the suite's metadata is used rather than a local copy of the rules
 *
 * `GET /api/runner/available` publishes, per module and per variant parameter, both the statically
 * applicable values and the conditional exclusions. Reading applicability from there keeps a single
 * source of truth - the suite - so a suite upgrade that changes a module's applicability is picked up
 * automatically instead of silently disagreeing with a transcription kept here.
 */
object WalletModuleApplicability {

    /**
     * Modules that only ever finish once the wallet's error screen has been evidenced, and that ask
     * for that evidence from `continueAfterRequestUriCalled()`.
     *
     * `AbstractVP1FinalWalletTest` calls that hook from `handleRequestUriRequest` alone, so it is
     * reachable only when the wallet fetches a `request_uri`. With `request_method=url_query` there is
     * no such fetch: the module creates no placeholder, nothing can be uploaded, and the test sits in
     * `WAITING` until the runner gives up - no matter how correctly the wallet behaves. (The wallet
     * cannot rescue it by responding either: `handleDirectPost` fails these modules by design.)
     *
     * Not expressible through the suite's own metadata, because the suite does not model it - hence a
     * harness-side rule rather than something derived from `@VariantNotApplicable`. It is the wallet
     * counterpart of the verifier-side `url_query` exclusions, which likewise arise from the suite
     * only populating `authorization_request_object` on the request-object code paths.
     */
    private val ERROR_PAGE_GATED_MODULES = setOf(
        "oid4vp-1final-wallet-negative-test-invalid-client-id-prefix",
        "oid4vp-1final-wallet-negative-test-invalid-request-object-signature",
        "oid4vp-1final-wallet-negative-test-mismatched-client-id",
        "oid4vp-1final-wallet-negative-test-missing-nonce",
        "oid4vp-1final-wallet-negative-test-redirect-uri-with-direct-post",
        "oid4vp-1final-wallet-negative-test-required-non-matching-credential",
        "oid4vp-1final-wallet-negative-test-response-uri-not-client-id",
        "oid4vp-1final-wallet-negative-test-unknown-transaction-data-type",
        "oid4vp-1final-wallet-negative-test-wrong-expected-origins",
    )

    /**
     * Published for non-HAIP variants, but the suite dies before the wallet is involved:
     * `CreateAuthorizationRequestSteps` requests missing condition
     * `AddVP1FinalEncryptionParametersToClientMetadata`. Not a wallet bug until the suite
     * actually builds the authorization request.
     */
    private const val ALTERNATE_HAPPY_FLOW = "oid4vp-1final-wallet-alternate-happy-flow"

    /**
     * Why [testModule] does not apply to [variantSelection], or `null` if it does.
     *
     * [variantSelection] must describe every axis - see [WalletTestPlan.axisValues].
     * [moduleMetadata] is the suite's description of the module, as returned by
     * `GET /api/runner/available`; an unknown module is still run, because a module this harness
     * cannot classify must be judged on its result rather than quietly dropped.
     */
    fun inapplicableReason(
        testModule: String,
        moduleMetadata: AvailableTestModule?,
        variantSelection: Map<String, String>,
    ): String? = suiteLimitationReason(testModule, variantSelection)
        ?: metadataReason(moduleMetadata, variantSelection)

    /** See [ERROR_PAGE_GATED_MODULES] and [ALTERNATE_HAPPY_FLOW]. */
    private fun suiteLimitationReason(
        testModule: String,
        variantSelection: Map<String, String>,
    ): String? {
        if (testModule == ALTERNATE_HAPPY_FLOW) {
            return "suite cannot build the authorization request: CreateAuthorizationRequestSteps " +
                "requests missing condition AddVP1FinalEncryptionParametersToClientMetadata"
        }
        if (variantSelection["request_method"] != "url_query") return null
        if (testModule !in ERROR_PAGE_GATED_MODULES) return null
        return "cannot be completed with request_method=url_query: the module only asks for its " +
                "error-screen evidence from continueAfterRequestUriCalled(), which no url_query " +
                "request ever reaches"
    }

    private fun metadataReason(
        moduleMetadata: AvailableTestModule?,
        variantSelection: Map<String, String>,
    ): String? =
        moduleMetadata?.variants?.entries?.firstNotNullOfOrNull { (parameter, axis) ->
            val applicable = axis.applicableValues(variantSelection)
            val selected = variantSelection[parameter]
            when {
                // The case the suite gets wrong: no value of this parameter can apply.
                applicable.isEmpty() ->
                    "no $parameter value applies to this variant"

                selected != null && selected !in applicable ->
                    "$parameter=$selected, applies only to ${applicable.sorted().joinToString()}"

                else -> null
            }
        }
}
