package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import id.walt.openid4vp.conformance.testplans.httpdata.AvailableTestModule
import id.walt.openid4vp.conformance.testplans.httpdata.AvailableTestModule.VariantAxis
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Applicability rules, pinned against the metadata `GET /api/runner/available` publishes for
 * conformance-suite `release-v5.2.2-10-g3f2bc7877`.
 *
 * The two "suite publishes it anyway" cases are the point of the exercise: they are the ones the
 * suite's own plan endpoint gets wrong, so a regression here silently reintroduces 28 module runs
 * that cannot pass. See [WalletModuleApplicability] for the upstream cause.
 */
class WalletModuleApplicabilityTest {

    private fun axis(
        values: List<String>,
        notApplicableWhen: Map<String, Map<String, Set<String>>> = emptyMap(),
    ) = VariantAxis(
        variantValues = values.associateWith { JsonObject(emptyMap()) },
        notApplicableWhen = notApplicableWhen,
    )

    /** `multisigned-one-invalid-signature` as the suite describes it. */
    private val multiSigned = AvailableTestModule(
        testName = "oid4vp-1final-wallet-multisigned-one-invalid-signature",
        variants = mapOf(
            // Static @VariantNotApplicable already left only the multi-signed request method...
            "request_method" to axis(
                values = listOf("request_uri_multisigned"),
                // ...and that one is excluded for every non-DC-API response mode.
                notApplicableWhen = mapOf(
                    "response_mode" to mapOf(
                        "direct_post" to setOf("request_uri_multisigned"),
                        "direct_post.jwt" to setOf("request_uri_multisigned"),
                        "dc_api" to setOf("url_query"),
                        "dc_api.jwt" to setOf("url_query"),
                    )
                ),
            ),
            "response_mode" to axis(listOf("dc_api", "dc_api.jwt", "direct_post", "direct_post.jwt")),
        ),
    )

    /** `negative-test-response-uri-not-client-id` as the suite describes it. */
    private val responseUriNotClientId = AvailableTestModule(
        testName = "oid4vp-1final-wallet-negative-test-response-uri-not-client-id",
        variants = mapOf(
            "client_id_prefix" to axis(
                values = listOf("redirect_uri", "web-origin"),
                notApplicableWhen = mapOf(
                    "request_method" to mapOf(
                        "request_uri_signed" to setOf("redirect_uri", "web-origin"),
                        "request_uri_multisigned" to setOf("redirect_uri", "web-origin"),
                    )
                ),
            ),
        ),
    )

    /** `ignores-unusable-encryption-key`: the suite filters this one correctly. */
    private val ignoresUnusableEncryptionKey = AvailableTestModule(
        testName = "oid4vp-1final-wallet-ignores-unusable-encryption-key",
        variants = mapOf(
            "response_mode" to axis(
                values = listOf("dc_api.jwt", "direct_post.jwt"),
                notApplicableWhen = mapOf(
                    "vp_profile" to mapOf("haip" to setOf("direct_post", "dc_api"))
                ),
            ),
        ),
    )

    private fun variant(
        clientIdPrefix: String = "x509_hash",
        requestMethod: String = "request_uri_signed",
        responseMode: String = "direct_post.jwt",
        vpProfile: String = "plain_vp",
    ) = WalletVariant(
        credentialFormat = "sd_jwt_vc",
        clientIdPrefix = clientIdPrefix,
        requestMethod = requestMethod,
        responseMode = responseMode,
        vpProfile = vpProfile,
    ).axisValues()

    @Test
    fun `multisigned module never applies to the response modes this harness drives`() {
        // The suite publishes it for all of these; every one dies on a missing request_object_json.
        listOf("direct_post", "direct_post.jwt").forEach { responseMode ->
            listOf("url_query", "request_uri_unsigned", "request_uri_signed").forEach { requestMethod ->
                val reason = WalletModuleApplicability.inapplicableReason(
                    multiSigned.testName!!,
                    multiSigned,
                    variant(requestMethod = requestMethod, responseMode = responseMode),
                )
                assertNotNull(reason, "multisigned must be skipped for $requestMethod + $responseMode")
            }
        }
    }

    @Test
    fun `multisigned module applies when the request really is multi-signed over the DC API`() {
        assertNull(
            WalletModuleApplicability.inapplicableReason(
                multiSigned.testName!!,
                multiSigned,
                variant(requestMethod = "request_uri_multisigned", responseMode = "dc_api.jwt"),
            )
        )
    }

    @Test
    fun `response_uri module is excluded for every prefix but redirect_uri and web-origin`() {
        // Signed requests exclude every prefix the module supports - the empty-set case the suite
        // mishandles.
        assertEquals(
            "no client_id_prefix value applies to this variant",
            WalletModuleApplicability.inapplicableReason(
                responseUriNotClientId.testName!!,
                responseUriNotClientId,
                variant(clientIdPrefix = "redirect_uri", requestMethod = "request_uri_signed"),
            )
        )
        // An x509 prefix is excluded outright, so the reason names the offending value. Checked with
        // an unsigned request method so the url_query rule below cannot mask it.
        assertEquals(
            "client_id_prefix=x509_hash, applies only to redirect_uri, web-origin",
            WalletModuleApplicability.inapplicableReason(
                responseUriNotClientId.testName!!,
                responseUriNotClientId,
                variant(clientIdPrefix = "x509_hash", requestMethod = "request_uri_unsigned"),
            )
        )
    }

    /**
     * The harness-side rule, which the suite's own metadata does not express. Both halves matter: the
     * gated modules must be skipped for url_query, and nothing else may be.
     */
    @Test
    fun `error-page gated modules are skipped for url_query only`() {
        val gated = "oid4vp-1final-wallet-negative-test-missing-nonce"
        val reason = WalletModuleApplicability.inapplicableReason(
            gated,
            moduleMetadata = null,
            variantSelection = variant(clientIdPrefix = "redirect_uri", requestMethod = "url_query"),
        )
        assertNotNull(reason)
        assertTrue(
            "continueAfterRequestUriCalled" in reason,
            "the reason must name the hook that url_query never reaches, was: $reason",
        )
        // The same module is perfectly runnable once a request_uri is fetched.
        assertNull(
            WalletModuleApplicability.inapplicableReason(
                gated,
                moduleMetadata = null,
                variantSelection = variant(requestMethod = "request_uri_signed"),
            )
        )
        // A module that does not gate on the error page is unaffected by url_query.
        assertNull(
            WalletModuleApplicability.inapplicableReason(
                "oid4vp-1final-wallet-happy-flow",
                moduleMetadata = null,
                variantSelection = variant(clientIdPrefix = "redirect_uri", requestMethod = "url_query"),
            )
        )
    }

    @Test
    fun `conditional exclusion only bites for the value it is declared for`() {
        // plain_vp leaves the encrypted response modes applicable...
        assertNull(
            WalletModuleApplicability.inapplicableReason(
                ignoresUnusableEncryptionKey.testName!!,
                ignoresUnusableEncryptionKey,
                variant(responseMode = "direct_post.jwt", vpProfile = "plain_vp"),
            )
        )
        // ...while an unencrypted mode was never applicable in the first place.
        assertNotNull(
            WalletModuleApplicability.inapplicableReason(
                ignoresUnusableEncryptionKey.testName!!,
                ignoresUnusableEncryptionKey,
                variant(responseMode = "direct_post", vpProfile = "plain_vp"),
            )
        )
    }

    @Test
    fun `a module the suite does not describe is run rather than dropped`() {
        assertNull(WalletModuleApplicability.inapplicableReason("oid4vp-1final-wallet-brand-new-module", null, variant()))
    }

    @Test
    fun `every driven matrix point keeps the positive modules`() {
        // Guards against an over-eager filter: a module with no restrictions must never be skipped.
        val unrestricted = AvailableTestModule(
            testName = "oid4vp-1final-wallet-happy-flow",
            variants = mapOf(
                "client_id_prefix" to axis(
                    listOf("redirect_uri", "x509_san_dns", "x509_hash", "decentralized_identifier")
                ),
                "request_method" to axis(listOf("url_query", "request_uri_unsigned", "request_uri_signed")),
                "response_mode" to axis(listOf("direct_post", "direct_post.jwt")),
                "vp_profile" to axis(listOf("plain_vp", "haip")),
                "credential_format" to axis(listOf("sd_jwt_vc", "iso_mdl")),
            ),
        )
        WalletVariantMatrix.all().forEach { matrixPoint ->
            assertNull(
                WalletModuleApplicability.inapplicableReason(
                    unrestricted.testName!!,
                    unrestricted,
                    matrixPoint.axisValues(),
                ),
                "happy-flow must run for ${matrixPoint.id}",
            )
        }
    }
}
