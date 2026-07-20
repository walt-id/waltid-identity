package id.walt.openid4vp.conformance.testplans.plans.vci.issuer

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class IssuerVariant(
    val fapiProfile: String,
    val credentialFormat: String,
    val grantType: String,
    val authorizationCodeFlowVariant: String,
    val clientAuthType: String,
    val senderConstrain: String,
    val authorizationRequestType: String,
    val requestMethod: String,
    val credentialEncryption: String,
) {
    val id: String
        get() = listOf(
            fapiProfile.toIdPart(),
            credentialFormat.toIdPart(),
            grantType.toIdPart(),
            authorizationCodeFlowVariant.toIdPart(),
            clientAuthType.toIdPart(),
            senderConstrain.toIdPart(),
            authorizationRequestType.toIdPart(),
            requestMethod.toIdPart(),
            credentialEncryption.toIdPart(),
        ).joinToString("-")

    val requiresCredentialOffer: Boolean
        get() = authorizationCodeFlowVariant == "issuer_initiated" || grantType == "pre_authorization_code"

    val description: String
        get() = listOf(
            "fapi_profile=$fapiProfile",
            "sender_constrain=$senderConstrain",
            "client_auth_type=$clientAuthType",
            "vci_authorization_code_flow_variant=$authorizationCodeFlowVariant",
            "credential_format=$credentialFormat",
            "authorization_request_type=$authorizationRequestType",
            "fapi_request_method=$requestMethod",
            "vci_grant_type=$grantType",
            "vci_credential_encryption=$credentialEncryption",
        ).joinToString(prefix = "OID4VCI 1.0 Issuer - ")

    val isDefinedByBaseIssuerPlan: Boolean
        get() = when {
            grantType == "pre_authorization_code" && authorizationCodeFlowVariant == "wallet_initiated" -> false
            fapiProfile == "vci_haip" && grantType == "pre_authorization_code" -> false
            fapiProfile == "vci_haip" && authorizationRequestType == "rar" -> false
            else -> true
        }

    fun toJsonObject(): JsonObject = buildJsonObject {
        put("fapi_profile", fapiProfile)
        put("sender_constrain", senderConstrain)
        put("client_auth_type", clientAuthType)
        put("vci_authorization_code_flow_variant", authorizationCodeFlowVariant)
        put("credential_format", credentialFormat)
        put("authorization_request_type", authorizationRequestType)
        put("fapi_request_method", requestMethod)
        put("vci_grant_type", grantType)
        put("vci_credential_encryption", credentialEncryption)
    }

    private fun String.toIdPart(): String = when (this) {
        "vci_haip" -> "vcihaip"
        "sd_jwt_vc" -> "sdjwt"
        "authorization_code" -> "authcode"
        "pre_authorization_code" -> "preauth"
        "wallet_initiated" -> "wallet"
        "issuer_initiated" -> "issuer"
        "client_attestation" -> "clientatt"
        "private_key_jwt" -> "privatekeyjwt"
        "plain_oauth" -> "oauth"
        "openid_connect" -> "openid"
        "signed_non_repudiation" -> "signednr"
        else -> replace("_", "")
    }
}

object IssuerVariantMatrix {
    fun all(): List<IssuerVariant> = combinations(
        fapiProfiles = listOf("vci"),
        credentialFormats = listOf("sd_jwt_vc", "mdoc"),
        grantTypes = listOf("authorization_code", "pre_authorization_code"),
        authorizationCodeFlowVariants = listOf("wallet_initiated", "issuer_initiated"),
        clientAuthTypes = listOf("client_attestation", "private_key_jwt", "mtls"),
        senderConstrains = listOf("dpop", "mtls"),
        authorizationRequestTypes = listOf("simple", "rar"),
        requestMethods = listOf("unsigned", "signed_non_repudiation"),
        credentialEncryptions = listOf("plain", "encrypted"),
    )

    private fun combinations(
        fapiProfiles: List<String>,
        credentialFormats: List<String>,
        grantTypes: List<String>,
        authorizationCodeFlowVariants: List<String>,
        clientAuthTypes: List<String>,
        senderConstrains: List<String>,
        authorizationRequestTypes: List<String>,
        requestMethods: List<String>,
        credentialEncryptions: List<String>,
    ): List<IssuerVariant> = buildList {
        fapiProfiles.forEach { fapiProfile ->
            credentialFormats.forEach { credentialFormat ->
                grantTypes.forEach { grantType ->
                    authorizationCodeFlowVariants.forEach { authorizationCodeFlowVariant ->
                        clientAuthTypes.forEach { clientAuthType ->
                            senderConstrains.forEach { senderConstrain ->
                                authorizationRequestTypes.forEach { authorizationRequestType ->
                                    requestMethods.forEach { requestMethod ->
                                        credentialEncryptions.forEach { credentialEncryption ->
                                            val variant = IssuerVariant(
                                                fapiProfile = fapiProfile,
                                                credentialFormat = credentialFormat,
                                                grantType = grantType,
                                                authorizationCodeFlowVariant = authorizationCodeFlowVariant,
                                                clientAuthType = clientAuthType,
                                                senderConstrain = senderConstrain,
                                                authorizationRequestType = authorizationRequestType,
                                                requestMethod = requestMethod,
                                                credentialEncryption = credentialEncryption,
                                            )
                                            if (variant.isDefinedByBaseIssuerPlan) {
                                                add(variant)
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
}

data class IssuerVariantSelection(
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
    val discoveryOnly: Boolean = false,
    val strictResults: Boolean = false,
    val reportDir: String = "build/reports/openid4vci-issuer-matrix",
) {
    fun select(variants: List<IssuerVariant>): List<IssuerVariant> {
        if (explicitVariantIds.isNotEmpty()) {
            return variants.filter { it.id in explicitVariantIds }
        }

        return variants.filter {
            fapiProfiles.matches(it.fapiProfile) &&
                credentialFormats.matches(it.credentialFormat) &&
                grantTypes.matches(it.grantType) &&
                authorizationCodeFlowVariants.matches(it.authorizationCodeFlowVariant) &&
                clientAuthTypes.matches(it.clientAuthType) &&
                senderConstrains.matches(it.senderConstrain) &&
                authorizationRequestTypes.matches(it.authorizationRequestType) &&
                requestMethods.matches(it.requestMethod) &&
                credentialEncryptions.matches(it.credentialEncryption)
        }
    }

    private fun Set<String>.matches(value: String) = isEmpty() || value in this

    companion object {
        fun fromEnvironment(): IssuerVariantSelection = IssuerVariantSelection(
            explicitVariantIds = csv("OPENID4VCI_CONFORMANCE_VARIANTS"),
            fapiProfiles = csv("OPENID4VCI_CONFORMANCE_FILTER_FAPI_PROFILES"),
            credentialFormats = csv("OPENID4VCI_CONFORMANCE_FILTER_FORMATS"),
            grantTypes = csv("OPENID4VCI_CONFORMANCE_FILTER_GRANT_TYPES"),
            authorizationCodeFlowVariants = csv("OPENID4VCI_CONFORMANCE_FILTER_FLOW_VARIANTS"),
            clientAuthTypes = csv("OPENID4VCI_CONFORMANCE_FILTER_CLIENT_AUTH_TYPES"),
            senderConstrains = csv("OPENID4VCI_CONFORMANCE_FILTER_SENDER_CONSTRAINTS"),
            authorizationRequestTypes = csv("OPENID4VCI_CONFORMANCE_FILTER_AUTH_REQUEST_TYPES"),
            requestMethods = csv("OPENID4VCI_CONFORMANCE_FILTER_REQUEST_METHODS"),
            credentialEncryptions = csv("OPENID4VCI_CONFORMANCE_FILTER_CREDENTIAL_ENCRYPTION"),
            discoveryOnly = bool("OPENID4VCI_CONFORMANCE_DISCOVERY_ONLY") ||
                env("OPENID4VCI_CONFORMANCE_MATRIX")?.equals("discovery", ignoreCase = true) == true,
            strictResults = bool("OPENID4VCI_CONFORMANCE_STRICT") || bool("OPENID4VCI_CONFORMANCE_CERTIFICATION_MODE"),
            reportDir = env("OPENID4VCI_CONFORMANCE_REPORT_DIR") ?: "build/reports/openid4vci-issuer-matrix",
        )

        private fun csv(name: String): Set<String> = env(name)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

        private fun bool(name: String): Boolean = env(name)
            ?.let { it.equals("true", ignoreCase = true) || it == "1" || it.equals("yes", ignoreCase = true) }
            ?: false

        private fun env(name: String): String? = System.getenv(name)?.ifBlank { null }
    }
}

@Serializable
enum class IssuerVariantRunStatus {
    GENERATED,
    BLOCKED,
    SUITE_INVALID,
    NOT_APPLICABLE,
    FAILED,
    PASSED,
}

@Serializable
data class IssuerVariantModuleRunResult(
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
data class IssuerVariantRunResult(
    val variantId: String,
    val variant: JsonObject,
    val status: IssuerVariantRunStatus,
    val planId: String? = null,
    val credentialOfferUri: String? = null,
    val modules: List<IssuerVariantModuleRunResult> = emptyList(),
    val error: String? = null,
)

@Serializable
data class IssuerVariantMatrixEntry(
    val variantId: String,
    val variant: JsonObject,
    val status: IssuerVariantRunStatus,
    val planId: String? = null,
    val error: String? = null,
)

object IssuerVariantReportWriter {
    private val json = Json { prettyPrint = true }

    fun write(reportDir: String, variants: List<IssuerVariant>, results: List<IssuerVariantRunResult>) {
        val dir = Path.of(reportDir)
        Files.createDirectories(dir)

        val resultsByVariant = results.associateBy { it.variantId }
        val matrixEntries = variants.map { variant ->
            val result = resultsByVariant[variant.id]
            IssuerVariantMatrixEntry(
                variantId = variant.id,
                variant = variant.toJsonObject(),
                status = result?.status ?: IssuerVariantRunStatus.GENERATED,
                planId = result?.planId,
                error = result?.error,
            )
        }

        Files.writeString(
            dir.resolve("matrix.json"),
            json.encodeToString(ListSerializer(IssuerVariantMatrixEntry.serializer()), matrixEntries)
        )
        Files.writeString(
            dir.resolve("results.json"),
            json.encodeToString(ListSerializer(IssuerVariantRunResult.serializer()), results)
        )
        Files.writeString(dir.resolve("summary.md"), buildSummary(results))
    }

    private fun buildSummary(results: List<IssuerVariantRunResult>): String = buildString {
        appendLine("# OpenID4VCI Issuer Matrix Summary")
        appendLine()
        appendLine("| Status | Count |")
        appendLine("|--------|-------|")
        IssuerVariantRunStatus.values().forEach { status ->
            appendLine("| `${status.name.lowercase()}` | ${results.count { it.status == status }} |")
        }
        appendLine()
        appendLine("| Variant | Status | Plan | Modules | Error |")
        appendLine("|---------|--------|------|---------|-------|")
        results.forEach { result ->
            appendLine(
                "| `${result.variantId}` | `${result.status.name.lowercase()}` | " +
                    "${result.planId ?: ""} | ${result.modules.size} | ${result.error?.sanitizeMarkdownCell() ?: ""} |"
            )
        }
    }

    private fun String.sanitizeMarkdownCell(): String = replace("\n", " ").replace("|", "\\|")
}

internal fun deriveIssuerCredentialProfileId(credentialConfigurationId: String): String = when {
    credentialConfigurationId.contains("photoID_credential") -> "photoIdCredentialSdJwt"
    credentialConfigurationId.contains("org.iso.23220.photoid") -> "isoPhotoId"
    credentialConfigurationId.contains("org.iso.18013.5.1.mDL") -> "isoMdl"
    credentialConfigurationId.contains("urn:eu.europa.ec.eudi:por:1") -> "powerOfRepresentationSdJwt"
    credentialConfigurationId.contains("urn:eu.europa.ec.eudi:cor:1") -> "companyRegistrationSdJwt"
    credentialConfigurationId.contains("urn:eudi:pid:1") -> "eudiPidSdJwt"
    credentialConfigurationId.contains("eu.europa.ec.eudi.pid.1") -> "eudiPidMdoc"
    credentialConfigurationId.contains("identity_credential") -> "identityCredentialSdJwt"
    credentialConfigurationId.contains("eu.europa.ec.av.1") -> "euAgeVerificationMdoc"
    else -> credentialConfigurationId
}
