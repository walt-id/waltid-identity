package id.walt.openid4vp.conformance.testplans.plans.vp.verifier

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import id.walt.openid4vp.conformance.testplans.plans.TestPlan
import id.walt.openid4vp.conformance.testplans.runner.req.ExpectedVerifierOutcome
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.UrlConfig
import id.walt.verifier2.data.Verification2Session.VerificationSessionRedirects
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * A single [VerifierVariant] turned into something [id.walt.openid4vp.conformance.testplans.runner.TestPlanRunner]
 * can execute: the conformance-suite plan configuration plus the matching Verifier2 session setup.
 *
 * One parameterised plan replaces the previous per-combination classes, so adding a point of the
 * matrix is a matrix entry rather than a new file that can drift out of sync with its own name.
 */
class Oid4vpVerifierVariantPlan(
    val variant: VerifierVariant,
    verifier2UrlPrefix: String,
    conformanceHost: String,
    conformancePort: Int,
) : TestPlan {

    val name: String get() = variant.id

    private val verifierKey = Json.decodeFromString<DirectSerializedKey>(TestKeyMaterial.SUITE_VERIFIER_SERIALIZED_KEY)

    /**
     * Only the leaf goes into `x5c`: per OID4VP-1FINAL-5.9.3 the trust anchor is configured out of
     * band (`request_object_trust_anchor_pem`) and must not be part of the chain.
     */
    private val verifierCertificateChain = listOf(TestKeyMaterial.SUITE_VERIFIER_LEAF_CERT)

    /**
     * `client_id` value without the prefix, as the conformance suite stores it in its configuration.
     * For `x509_san_dns` this is the leaf certificate's SAN DNS entry; for `x509_hash` it is the
     * base64url SHA-256 of the leaf's DER, derived rather than hardcoded so it follows the cert.
     *
     * `redirect_uri` has no static value: OID4VP 1.0 §5.9.3-3.1.1 makes the identifier the per-session
     * Response URI. The suite does not need one either - it declares `client.client_id` a required
     * configuration field only for `x509_san_dns`.
     */
    private val clientIdValue: String? = when (variant.clientIdPrefix) {
        "x509_san_dns" -> VERIFIER_SAN_DNS
        "x509_hash" -> X509Hash.hashOfCertificate(TestKeyMaterial.SUITE_VERIFIER_LEAF_CERT.decodeFromBase64())
        "redirect_uri" -> null
        else -> error("Unsupported client_id_prefix for Verifier2: ${variant.clientIdPrefix}")
    }

    /**
     * Client identifier handed to Verifier2.
     *
     * For `redirect_uri` only the bare prefix is passed: `VerificationSessionCreator` completes it
     * with the Response URI once the session id exists, which is the only point at which the value
     * §5.9.3-3.1.1 mandates is known.
     */
    private val clientId = clientIdValue
        ?.let { "${variant.clientIdPrefix}:$it" }
        ?: variant.clientIdPrefix

    /** Omitted entirely for `redirect_uri`, which has no static client identifier to declare. */
    private val clientIdConfigEntry = clientIdValue?.let { """"client_id": "$it",""" } ?: ""

    private val dcqlQuery = if (variant.isMdoc) MDOC_DCQL else SD_JWT_VC_DCQL

    /**
     * Credential-issuance material the suite needs to mint the credential it will present.
     *
     * Only SD-JWT VC needs a signing key here; for `iso_mdl` the suite generates its own document
     * signer, so passing one would be misleading dead configuration.
     */
    private val credentialConfig = if (variant.isMdoc) "" else """
        "credential": {
            "signing_jwk": ${TestKeyMaterial.SDJWT_ISSUER_KEY_WITH_X5C}
        },
    """.trimIndent()

    override val config = TestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", variant.conformanceTestPlanName)
            append("variant", variant.testPlanCreationVariant().toString())
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                $credentialConfig
                "client": {
                    $clientIdConfigEntry
                    "request_object_trust_anchor_pem": ${TestKeyMaterial.CREDENTIAL_ISSUER_CA_PEM_JSON}
                },
                "description": "${variant.description}",
                "server": {
                    "authorization_endpoint": "https://$conformanceHost:$conformancePort"
                },
                "publish": "everything"
            }
            """.trimIndent()
        ),

        moduleOutcomes = moduleOutcomes(variant),

        // url_query means the request travels complete in the URL rather than behind a request_uri.
        presentUsingFullRequestUrl = !variant.signedRequest,

        verificationSessionSetup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = Json.decodeFromString(dcqlQuery),
                policies = ConformanceVerifierPolicies.withoutMdocIssuerAuth(),

                signedRequest = variant.signedRequest,
                encryptedResponse = variant.encryptedResponse,

                clientId = clientId,

                key = verifierKey,
                x5c = verifierCertificateChain,
            ),
            urlConfig = UrlConfig(
                urlPrefix = verifier2UrlPrefix,
                // urlHost is set by TestPlanRunner from the suite's exposed authorization endpoint
            ),
            redirects = VerificationSessionRedirects(
                successRedirectUri = Url("https://example.org/verification-success")
            ),
        ),
    )

    companion object {
        /** SAN DNS entry of [TestKeyMaterial.VERIFIER_LEAF_CERT]. */
        private const val VERIFIER_SAN_DNS = "verifier.example.com"

        // language=JSON
        private val SD_JWT_VC_DCQL = """
            {
                "credentials": [
                    {
                        "id": "pid",
                        "format": "dc+sd-jwt",
                        "meta": {
                            "vct_values": ["https://credentials.example.com/identity_credential"]
                        },
                        "claims": [
                            {"path": ["given_name"]},
                            {"path": ["family_name"]},
                            {"path": ["birthdate"]},
                            {"path": ["age_in_years"]}
                        ]
                    }
                ]
            }
        """.trimIndent()

        // language=JSON
        private val MDOC_DCQL = """
            {
                "credentials": [
                    {
                        "id": "my_mdl",
                        "format": "mso_mdoc",
                        "meta": {
                            "doctype_value": "org.iso.18013.5.1.mDL"
                        },
                        "claims": [
                            {"path": ["org.iso.18013.5.1", "family_name"]},
                            {"path": ["org.iso.18013.5.1", "given_name"]},
                            {"path": ["org.iso.18013.5.1", "birth_date"]},
                            {"path": ["org.iso.18013.5.1", "issuing_country"]}
                        ]
                    }
                ]
            }
        """.trimIndent()

        /**
         * Expected verifier behaviour per conformance-suite module.
         *
         * Declared explicitly so a module the suite adds later shows up as undeclared in the run
         * output instead of silently defaulting to a pass. Modules the suite does not include for a
         * given variant are simply never looked up.
         */
        private fun moduleOutcomes(variant: VerifierVariant): Map<String, ExpectedVerifierOutcome> = buildMap {
            // Positive modules: a valid presentation must be accepted.
            put("oid4vp-1final-verifier-happy-flow", ExpectedVerifierOutcome.ACCEPT)
            put("oid4vp-1final-verifier-minimal-cnf-jwk", ExpectedVerifierOutcome.ACCEPT)
            put("oid4vp-1final-verifier-request-uri-fetched-twice", ExpectedVerifierOutcome.ACCEPT)
            // request_uri_method=post is optional for a verifier to support.
            put("oid4vp-1final-verifier-request-uri-method-post", ExpectedVerifierOutcome.ACCEPT_OR_SKIP)

            // Negative modules: the verifier must reject the presentation.
            if (variant.isMdoc) {
                put("oid4vp-1final-verifier-invalid-session-transcript", ExpectedVerifierOutcome.REJECT)
            } else {
                put("oid4vp-1final-verifier-invalid-kb-jwt-signature", ExpectedVerifierOutcome.REJECT)
                put("oid4vp-1final-verifier-invalid-credential-signature", ExpectedVerifierOutcome.REJECT)
                put("oid4vp-1final-verifier-invalid-sd-hash", ExpectedVerifierOutcome.REJECT)
                put("oid4vp-1final-verifier-invalid-kb-jwt-nonce", ExpectedVerifierOutcome.REJECT)
                put("oid4vp-1final-verifier-invalid-kb-jwt-aud", ExpectedVerifierOutcome.REJECT)
                put("oid4vp-1final-verifier-kb-jwt-iat-in-past", ExpectedVerifierOutcome.REJECT)
                put("oid4vp-1final-verifier-kb-jwt-iat-in-future", ExpectedVerifierOutcome.REJECT)
            }
        }
    }
}
