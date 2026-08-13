package id.walt.openid4vp.conformance.testplans.plans.vp.verifier

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One point of the OpenID4VP 1.0 verifier conformance matrix.
 *
 * Replaces the previous approach of hand-writing a class per combination, which had produced nine
 * classes encoding only two distinct configurations, several of them contradicting their own names.
 * Mirrors the pattern already used for the issuer side in
 * [id.walt.openid4vp.conformance.testplans.plans.vci.issuer.IssuerVariantMatrix].
 */
@Serializable
data class VerifierVariant(
    val credentialFormat: String,
    val clientIdPrefix: String,
    val requestMethod: String,
    val responseMode: String,
    val vpProfile: String,
) {
    val isHaip: Boolean get() = vpProfile == "haip"

    val isMdoc: Boolean get() = credentialFormat == "iso_mdl"

    /** The verifier must sign the request object unless the request is passed unsigned in the URL. */
    val signedRequest: Boolean get() = requestMethod != "url_query"

    /** `direct_post.jwt` is the encrypted response mode. */
    val encryptedResponse: Boolean get() = responseMode == "direct_post.jwt"

    val conformanceTestPlanName: String
        get() = if (isHaip) "oid4vp-1final-verifier-haip-test-plan" else "oid4vp-1final-verifier-test-plan"

    val id: String
        get() = listOf(
            credentialFormat.toIdPart(),
            clientIdPrefix.toIdPart(),
            requestMethod.toIdPart(),
            responseMode.toIdPart(),
            vpProfile.toIdPart(),
        ).joinToString("-")

    val description: String
        get() = "Verifier - $credentialFormat + $clientIdPrefix + $requestMethod + $vpProfile + $responseMode"

    /**
     * Variant to send when creating the test plan.
     *
     * The HAIP plan fixes `client_id_prefix`, `request_method` and `vp_profile` itself and rejects
     * them being restated, so only the two free axes are passed there.
     */
    fun testPlanCreationVariant(): JsonObject = buildJsonObject {
        put("credential_format", credentialFormat)
        put("response_mode", responseMode)
        if (!isHaip) {
            put("client_id_prefix", clientIdPrefix)
            put("request_method", requestMethod)
            put("vp_profile", vpProfile)
        }
    }

    private fun String.toIdPart(): String = when (this) {
        "sd_jwt_vc" -> "sdjwt"
        "iso_mdl" -> "mdl"
        "request_uri_signed" -> "requrisigned"
        "url_query" -> "urlquery"
        "direct_post.jwt" -> "directpostjwt"
        "direct_post" -> "directpost"
        else -> replace("_", "").replace(".", "")
    }
}

object VerifierVariantMatrix {

    private val credentialFormats = listOf("sd_jwt_vc", "iso_mdl")
    private val clientIdPrefixes = listOf("redirect_uri", "x509_san_dns", "x509_hash")
    private val requestMethods = listOf("url_query", "request_uri_signed")
    private val responseModes = listOf("direct_post", "direct_post.jwt")

    /**
     * Every combination the conformance suite accepts.
     *
     * HAIP is generated separately because its plan fixes three of the five axes.
     */
    fun all(): List<VerifierVariant> = plainVp() + haip()

    fun plainVp(): List<VerifierVariant> = buildList {
        credentialFormats.forEach { credentialFormat ->
            clientIdPrefixes.forEach { clientIdPrefix ->
                requestMethods.forEach { requestMethod ->
                    responseModes.forEach { responseMode ->
                        val variant = VerifierVariant(
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

    /** HAIP always means x509_hash + a signed request + an encrypted response. */
    fun haip(): List<VerifierVariant> = credentialFormats.map { credentialFormat ->
        VerifierVariant(
            credentialFormat = credentialFormat,
            clientIdPrefix = "x509_hash",
            requestMethod = "request_uri_signed",
            responseMode = "direct_post.jwt",
            vpProfile = "haip",
        )
    }

    /**
     * Combinations the conformance suite declares not applicable, transcribed from
     * `AbstractVP1FinalVerifierTest`'s `@VariantNotApplicableWhen` annotations so that we never ask
     * the suite for a plan it would reject with "No test modules ... applicable".
     */
    fun isApplicable(variant: VerifierVariant): Boolean = when {
        // HAIP mandates an encrypted response.
        variant.isHaip && variant.responseMode == "direct_post" -> false

        // OID4VP 1.0 Final 5.9.3-3.1.1: redirect_uri client identifiers cannot use signed requests,
        // because there is no key to authenticate the verifier with.
        variant.clientIdPrefix == "redirect_uri" && variant.requestMethod == "request_uri_signed" -> false

        // OID4VP 1.0 Final 5.9.3-3.5.1 (x509_san_dns) and 5.9.3-3.6.1 (x509_hash): the request must
        // be signed, so it cannot be delivered unsigned in the URL query.
        variant.clientIdPrefix in setOf("x509_san_dns", "x509_hash") && variant.requestMethod == "url_query" -> false

        // Two suite conditions read their inputs from `authorization_request_object`, which only the
        // request-object-extracting conditions ever populate (FetchRequestUriAndExtractRequestObject,
        // PostToRequestUriAndExtractRequestObject, AbstractExtractRequestObject). A `url_query`
        // request has no request object at all - AbstractVP1FinalVerifierTest says so itself: "there
        // is no signed request object in the url_query method" - so these modules error out before
        // Verifier2 is exercised:
        //
        //  - iso_mdl: CreateVP1FinalVerifierIsoMdocRedirectSessionTranscript* builds the mdoc
        //    SessionTranscript from the request object.
        //  - direct_post.jwt: VP1FinalEncryptVPResponse reads the response-encryption key from
        //    `claims.client_metadata.jwks` of the request object. Verifier2 does put that key in
        //    `client_metadata` and serialises it into the URL; the suite simply never looks there.
        //
        // Both are suite limitations, not Verifier2 gaps, and neither is fixable from this side.
        variant.requestMethod == "url_query" && (variant.isMdoc || variant.encryptedResponse) -> false

        else -> true
    }
}
