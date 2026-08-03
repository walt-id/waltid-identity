package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

/**
 * A suite-supported OpenID4VCI wallet test-plan context.
 *
 * Basic VCI variants are fully selected at plan creation. The HAIP plan fixes
 * most protocol parameters internally; its immediate/deferred/encrypted
 * modules are selected after the plan has been created.
 */
@Serializable
data class WalletVariant(
    val fapiProfile: String,
    val credentialFormat: String,
    val grantType: String,
    val authorizationCodeFlowVariant: String,
    val clientAuthType: String,
    val senderConstrain: String,
    val authorizationRequestType: String,
    val requestMethod: String,
    val credentialEncryption: String,
    val credentialIssuanceMode: String,
    val credentialOfferVariant: String? = null,
) {
    val isHaip: Boolean
        get() = fapiProfile == "vci_haip"

    val conformanceTestPlanName: String
        get() = if (isHaip) "oid4vci-1_0-wallet-haip-test-plan" else "oid4vci-1_0-wallet-test-plan"

    val id: String
        get() = listOfNotNull(
            fapiProfile.toIdPart(),
            credentialFormat.toIdPart(),
            grantType.toIdPart(),
            authorizationCodeFlowVariant.toIdPart(),
            clientAuthType.toIdPart(),
            senderConstrain.toIdPart(),
            authorizationRequestType.toIdPart(),
            requestMethod.toIdPart(),
            credentialIssuanceMode.toIdPart(),
            credentialEncryption.toIdPart(),
            credentialOfferVariant?.toIdPart(),
        ).joinToString("-")

    val description: String
        get() = listOfNotNull(
            "fapi_profile=$fapiProfile",
            "credential_format=$credentialFormat",
            "vci_grant_type=$grantType",
            "vci_authorization_code_flow_variant=$authorizationCodeFlowVariant",
            "client_auth_type=$clientAuthType",
            "sender_constrain=$senderConstrain",
            "authorization_request_type=$authorizationRequestType",
            "fapi_request_method=$requestMethod",
            "vci_credential_issuance_mode=$credentialIssuanceMode",
            "vci_credential_encryption=$credentialEncryption",
            credentialOfferVariant?.let { "vci_credential_offer_variant=$it" },
        ).joinToString(prefix = "OID4VCI 1.0 Wallet - ")

    fun toJsonObject(): JsonObject = buildJsonObject {
        put("fapi_profile", fapiProfile)
        put("credential_format", credentialFormat)
        put("vci_grant_type", grantType)
        put("vci_authorization_code_flow_variant", authorizationCodeFlowVariant)
        put("client_auth_type", clientAuthType)
        put("sender_constrain", senderConstrain)
        put("authorization_request_type", authorizationRequestType)
        put("fapi_request_method", requestMethod)
        put("vci_credential_issuance_mode", credentialIssuanceMode)
        put("vci_credential_encryption", credentialEncryption)
        credentialOfferVariant?.let { put("vci_credential_offer_variant", it) }
    }

    /** The HAIP plan accepts only its context selectors at plan creation. */
    fun testPlanCreationVariant(): JsonObject =
        if (!isHaip) {
            toJsonObject()
        } else {
            buildJsonObject {
                put("credential_format", credentialFormat)
                put("vci_authorization_code_flow_variant", authorizationCodeFlowVariant)
                credentialOfferVariant?.let { put("vci_credential_offer_variant", it) }
            }
        }

    private fun String.toIdPart(): String = when (this) {
        "vci_haip" -> "vcihaip"
        "sd_jwt_vc" -> "sdjwt"
        "authorization_code" -> "authcode"
        "pre_authorization_code" -> "preauth"
        "wallet_initiated" -> "wallet"
        "issuer_initiated" -> "issuer"
        "issuer_initiated_dc_api" -> "issuerdcapi"
        "private_key_jwt" -> "privatekeyjwt"
        "client_attestation" -> "clientatt"
        "signed_non_repudiation" -> "signednr"
        "by_value" -> "byvalue"
        "by_reference" -> "byreference"
        else -> replace("_", "")
    }
}

object WalletVariantMatrix {
    /**
     * The basic plan exposes all of these dimensions. This is 1,728 plan
     * contexts, so callers should normally select a subset with filters.
     */
    fun basic(): List<WalletVariant> = buildList {
        val formats = listOf("sd_jwt_vc", "mdoc")
        val clientAuthTypes = listOf("private_key_jwt", "mtls", "client_attestation")
        val senderConstraints = listOf("dpop", "mtls")
        val authorizationRequestTypes = listOf("simple", "rar")
        val requestMethods = listOf("unsigned", "signed_non_repudiation")
        val credentialEncryptions = listOf("plain", "encrypted")
        val issuanceModes = listOf("immediate", "deferred")

        formats.forEach { format ->
            clientAuthTypes.forEach { clientAuthType ->
                senderConstraints.forEach { senderConstrain ->
                    authorizationRequestTypes.forEach { authorizationRequestType ->
                        requestMethods.forEach { requestMethod ->
                            credentialEncryptions.forEach { credentialEncryption ->
                                issuanceModes.forEach { issuanceMode ->
                                    add(
                                        WalletVariant(
                                            fapiProfile = "vci",
                                            credentialFormat = format,
                                            grantType = "authorization_code",
                                            authorizationCodeFlowVariant = "wallet_initiated",
                                            clientAuthType = clientAuthType,
                                            senderConstrain = senderConstrain,
                                            authorizationRequestType = authorizationRequestType,
                                            requestMethod = requestMethod,
                                            credentialEncryption = credentialEncryption,
                                            credentialIssuanceMode = issuanceMode,
                                        )
                                    )

                                    listOf("issuer_initiated", "issuer_initiated_dc_api").forEach { flowVariant ->
                                        listOf("authorization_code", "pre_authorization_code").forEach { grantType ->
                                            listOf("by_value", "by_reference").forEach { offerVariant ->
                                                add(
                                                    WalletVariant(
                                                        fapiProfile = "vci",
                                                        credentialFormat = format,
                                                        grantType = grantType,
                                                        authorizationCodeFlowVariant = flowVariant,
                                                        clientAuthType = clientAuthType,
                                                        senderConstrain = senderConstrain,
                                                        authorizationRequestType = authorizationRequestType,
                                                        requestMethod = requestMethod,
                                                        credentialEncryption = credentialEncryption,
                                                        credentialIssuanceMode = issuanceMode,
                                                        credentialOfferVariant = offerVariant,
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * HAIP fixes client-attestation, DPoP, simple/unsigned authorization-code
     * behaviour inside the upstream plan. It exposes only format, initiation
     * mode, and issuer-initiated offer delivery as plan selectors.
     */
    fun haip(): List<WalletVariant> = buildList {
        listOf("sd_jwt_vc", "mdoc").forEach { format ->
            add(haipVariant(format, "wallet_initiated"))
            listOf("by_value", "by_reference").forEach { offerVariant ->
                add(haipVariant(format, "issuer_initiated", offerVariant))
            }
        }
    }

    fun all(): List<WalletVariant> = basic() + haip()

    private fun haipVariant(
        credentialFormat: String,
        authorizationCodeFlowVariant: String,
        credentialOfferVariant: String? = null,
    ) = WalletVariant(
        fapiProfile = "vci_haip",
        credentialFormat = credentialFormat,
        grantType = "authorization_code",
        authorizationCodeFlowVariant = authorizationCodeFlowVariant,
        clientAuthType = "client_attestation",
        senderConstrain = "dpop",
        authorizationRequestType = "simple",
        requestMethod = "unsigned",
        credentialEncryption = "suite_matrix",
        credentialIssuanceMode = "suite_matrix",
        credentialOfferVariant = credentialOfferVariant,
    )
}

data class WalletVariantSelection(
    val explicitVariantIds: Set<String> = emptySet(),
    val fapiProfiles: Set<String> = emptySet(),
    val credentialFormats: Set<String> = emptySet(),
    val grantTypes: Set<String> = emptySet(),
    val authorizationCodeFlowVariants: Set<String> = emptySet(),
    val clientAuthTypes: Set<String> = emptySet(),
    val senderConstrains: Set<String> = emptySet(),
    val authorizationRequestTypes: Set<String> = emptySet(),
    val requestMethods: Set<String> = emptySet(),
    val credentialEncryptions: Set<String> = emptySet(),
    val credentialIssuanceModes: Set<String> = emptySet(),
    val credentialOfferVariants: Set<String> = emptySet(),
    val moduleGroups: Set<String> = setOf("all"),
    val explicitModules: Set<String> = emptySet(),
    val strictResults: Boolean = true,
    val reportDir: String = "build/reports/openid4vci-wallet-matrix",
) {
    fun select(variants: List<WalletVariant>): List<WalletVariant> {
        if (explicitVariantIds.isNotEmpty()) {
            return variants.filter { it.id in explicitVariantIds }
        }

        return variants.filter { variant ->
            fapiProfiles.matches(variant.fapiProfile) &&
                credentialFormats.matches(variant.credentialFormat) &&
                grantTypes.matches(variant.grantType) &&
                authorizationCodeFlowVariants.matches(variant.authorizationCodeFlowVariant) &&
                clientAuthTypes.matches(variant.clientAuthType) &&
                senderConstrains.matches(variant.senderConstrain) &&
                authorizationRequestTypes.matches(variant.authorizationRequestType) &&
                requestMethods.matches(variant.requestMethod) &&
                variant.matchesPlanContextFilter(credentialEncryptions, variant.credentialEncryption) &&
                variant.matchesPlanContextFilter(credentialIssuanceModes, variant.credentialIssuanceMode) &&
                credentialOfferVariants.matches(variant.credentialOfferVariant)
        }
    }

    /** Filter a module after the suite has added HAIP's internal variants. */
    fun selectsModule(moduleName: String, moduleVariant: JsonObject, planVariant: WalletVariant): Boolean {
        if (explicitModules.isNotEmpty() && moduleName !in explicitModules) return false
        if (explicitModules.isEmpty() && !moduleGroups.matchesModule(moduleName)) return false

        fun value(name: String): String? = moduleVariant[name]?.toString()?.trim('"')
            ?: planVariant.toJsonObject()[name]?.toString()?.trim('"')

        return fapiProfiles.matches(value("fapi_profile")) &&
            credentialFormats.matches(value("credential_format")) &&
            grantTypes.matches(value("vci_grant_type")) &&
            authorizationCodeFlowVariants.matches(value("vci_authorization_code_flow_variant")) &&
            clientAuthTypes.matches(value("client_auth_type")) &&
            senderConstrains.matches(value("sender_constrain")) &&
            authorizationRequestTypes.matches(value("authorization_request_type")) &&
            requestMethods.matches(value("fapi_request_method")) &&
            credentialEncryptions.matches(value("vci_credential_encryption")) &&
            credentialIssuanceModes.matches(value("vci_credential_issuance_mode")) &&
            credentialOfferVariants.matches(value("vci_credential_offer_variant"))
    }

    private fun WalletVariant.matchesPlanContextFilter(filter: Set<String>, value: String): Boolean =
        filter.isEmpty() || value == "suite_matrix" || value in filter

    private fun Set<String>.matches(value: String?): Boolean = isEmpty() || value in this

    private fun Set<String>.matchesModule(moduleName: String): Boolean =
        "all" in this || any { group ->
            when (group) {
                "issuance", "positive" -> moduleName == "oid4vci-1_0-wallet-test-credential-issuance"
                "notification" -> moduleName == "oid4vci-1_0-wallet-test-credential-issuance-notification"
                "scopes" -> moduleName.contains("scopes-without-authorization-details")
                "client-attestation" -> moduleName == "oid4vci-1_0-wallet-test-client-attestation-challenge"
                "batch" -> moduleName == "oid4vci-1_0-wallet-test-batch-credential-issuance"
                "fapi" -> moduleName.startsWith("fapi2-")
                else -> error("Unsupported OPENID4VCI_WALLET_CONFORMANCE_MODULE_GROUPS value '$group'.")
            }
        }

    companion object {
        fun fromEnvironment(): WalletVariantSelection = WalletVariantSelection(
            explicitVariantIds = csv("OPENID4VCI_WALLET_CONFORMANCE_VARIANTS"),
            fapiProfiles = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_FAPI_PROFILES"),
            credentialFormats = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_FORMATS"),
            grantTypes = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_GRANT_TYPES"),
            authorizationCodeFlowVariants = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_FLOW_VARIANTS"),
            clientAuthTypes = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES"),
            senderConstrains = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_SENDER_CONSTRAINTS"),
            authorizationRequestTypes = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES"),
            requestMethods = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_REQUEST_METHODS"),
            credentialEncryptions = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION"),
            credentialIssuanceModes = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_ISSUANCE_MODES"),
            credentialOfferVariants = csv("OPENID4VCI_WALLET_CONFORMANCE_FILTER_OFFER_VARIANTS"),
            moduleGroups = csv("OPENID4VCI_WALLET_CONFORMANCE_MODULE_GROUPS").ifEmpty { setOf("all") },
            explicitModules = csv("OPENID4VCI_WALLET_CONFORMANCE_MODULES"),
            strictResults = optionalBool("OPENID4VCI_WALLET_CONFORMANCE_STRICT") ?: true,
            reportDir = env("OPENID4VCI_WALLET_CONFORMANCE_REPORT_DIR") ?: "build/reports/openid4vci-wallet-matrix",
        )

        private fun csv(name: String): Set<String> = env(name)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

        private fun optionalBool(name: String): Boolean? = env(name)?.let { value ->
            when {
                value.equals("true", true) || value == "1" || value.equals("yes", true) || value.equals("on", true) -> true
                value.equals("false", true) || value == "0" || value.equals("no", true) || value.equals("off", true) -> false
                else -> error("Unsupported $name value '$value'. Expected true or false.")
            }
        }

        private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

@Serializable
enum class WalletVariantRunStatus {
    BLOCKED,
    FAILED,
    PASSED,
}

@Serializable
data class WalletVariantModuleRunResult(
    val testModule: String,
    val testId: String? = null,
    val logUrl: String? = null,
    val status: String? = null,
    val result: String? = null,
    val accepted: Boolean = false,
    val error: String? = null,
    val variant: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class WalletVariantRunResult(
    val variantId: String,
    val variant: JsonObject,
    val status: WalletVariantRunStatus,
    val planId: String? = null,
    val modules: List<WalletVariantModuleRunResult> = emptyList(),
    val error: String? = null,
)

object WalletVariantReportWriter {
    private val json = kotlinx.serialization.json.Json { prettyPrint = true }

    fun write(reportDir: String, results: List<WalletVariantRunResult>) {
        val dir = Path.of(reportDir)
        Files.createDirectories(dir)
        Files.writeString(
            dir.resolve("results.json"),
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(WalletVariantRunResult.serializer()), results)
        )
        Files.writeString(dir.resolve("summary.md"), buildString {
            appendLine("# OpenID4VCI Wallet Matrix Summary")
            appendLine()
            appendLine("| Variant | Status | Plan | Modules | Error |")
            appendLine("|---------|--------|------|---------|-------|")
            results.forEach { result ->
                appendLine(
                    "| `${result.variantId}` | `${result.status.name.lowercase()}` | " +
                        "${result.planId.orEmpty()} | ${result.modules.size} | " +
                        "${result.error.orEmpty().replace("\n", " ").replace("|", "\\|")} |"
                )
            }
        })
    }
}
