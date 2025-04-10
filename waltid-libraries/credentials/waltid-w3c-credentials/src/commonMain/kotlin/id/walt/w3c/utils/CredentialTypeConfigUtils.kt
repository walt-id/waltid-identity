package id.walt.w3c.utils

import id.walt.sdjwt.SDJWTVCTypeMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
enum class CredentialFormat(val value: String) {
    jwt_vc_json("jwt-vc-json"),
    sd_jwt_vc("sd-jwt-vc"),
    mso_mdoc("mso-mdoc")
}

@Serializable
data class CredentialSupported(
    val format: CredentialFormat,
    val cryptographicBindingMethodsSupported: Set<String> = emptySet(),
    val credentialSigningAlgValuesSupported: Set<String> = emptySet(),
    val types: List<String> = emptyList(),
    val credentialDefinition: CredentialDefinition? = null,
    val display: List<DisplayProperties> = emptyList(),
    val vct: String? = null,
    val docType: String? = null,
    val sdJwtVcTypeMetadata: SDJWTVCTypeMetadata? = null
)

@Serializable
data class CredentialDefinition(
    val type: List<String> = emptyList()
)

@Serializable
data class DisplayProperties(
    val name: String,
    val description: String,
    val logo: LogoProperties,
    val backgroundColor: String,
    val textColor: String
)

@Serializable
data class LogoProperties(
    val url: String,
    val altText: String
)

@Serializable
data class SDJWTVCTypeMetadata(
    val vct: String,
    val name: String,
    val description: String
)

private fun vc(credentialSupported: CredentialSupported) = Json.encodeToJsonElement(credentialSupported)


@Serializable
data class CredentialTypeConfig(
    val supportedCredentialTypes: Map<String, JsonElement> = mapOf(
        "testCredential+jwt-vc-json" to vc(
            CredentialSupported(
                format = CredentialFormat.jwt_vc_json,
                cryptographicBindingMethodsSupported = setOf("did"),
                credentialSigningAlgValuesSupported = setOf("EdDSA", "ES256", "ES256K", "RSA"),
                types = listOf("VerifiableCredential", "TestCredential"),
                credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "TestCredential")),
                display = listOf(
                    DisplayProperties(
                        name = "Test Credential",
                        description = "This is a test credential",
                        logo = LogoProperties(
                            url = "https://example.com/logo.png",
                            altText = "Logo"
                        ),
                        backgroundColor = "#FFFFFF",
                        textColor = "#000000"
                    )
                ),
            )
        ),
    ),
)