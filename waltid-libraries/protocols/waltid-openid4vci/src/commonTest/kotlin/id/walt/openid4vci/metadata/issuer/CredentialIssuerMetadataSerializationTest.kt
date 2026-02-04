package id.walt.openid4vci.metadata.issuer

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.prooftypes.ProofTypeId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CredentialIssuerMetadataSerializationTest {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    // 1) Required fields: credential_issuer, credential_endpoint, credential_configurations_supported
    @Test
    fun `serializes required issuer fields`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(id = "cred-id-1", format = CredentialFormat.SD_JWT_VC),
            ),
        )

        val jsonObject = json.parseToJsonElement(json.encodeToString(metadata)).jsonObject

        assertEquals("https://issuer.example", jsonObject["credential_issuer"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example/credential", jsonObject["credential_endpoint"]?.jsonPrimitive?.content)
        assertTrue(jsonObject["credential_configurations_supported"] is JsonObject)
    }

    // 2) authorization_servers array
    @Test
    fun `serializes authorization servers as array`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            authorizationServers = listOf("https://auth.example"),
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(id = "cred-id-1", format = CredentialFormat.SD_JWT_VC),
            ),
        )

        val jsonObject = json.parseToJsonElement(json.encodeToString(metadata)).jsonObject
        val authorizationServers = jsonObject["authorization_servers"] as? JsonArray

        assertNotNull(authorizationServers)
        assertEquals(listOf("https://auth.example"), authorizationServers.map { it.jsonPrimitive.content })
    }

    // 2b) authorization_servers array with multiple entries
    @Test
    fun `serializes multiple authorization servers`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            authorizationServers = listOf("https://auth.example", "https://auth2.example"),
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(id = "cred-id-1", format = CredentialFormat.SD_JWT_VC),
            ),
        )

        val jsonObject = json.parseToJsonElement(json.encodeToString(metadata)).jsonObject
        val authorizationServers = jsonObject["authorization_servers"] as? JsonArray

        assertNotNull(authorizationServers)
        assertEquals(
            listOf("https://auth.example", "https://auth2.example"),
            authorizationServers.map { it.jsonPrimitive.content },
        )
    }

    // 3) Optional issuer endpoints
    @Test
    fun `serializes optional issuer endpoints`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            deferredCredentialEndpoint = "https://issuer.example/credential_deferred",
            notificationEndpoint = "https://issuer.example/notification",
            nonceEndpoint = "https://issuer.example/nonce",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(id = "cred-id-1", format = CredentialFormat.SD_JWT_VC),
            ),
        )

        val jsonObject = json.parseToJsonElement(json.encodeToString(metadata)).jsonObject

        assertEquals(
            "https://issuer.example/credential_deferred",
            jsonObject["deferred_credential_endpoint"]?.jsonPrimitive?.content,
        )
        assertEquals("https://issuer.example/notification", jsonObject["notification_endpoint"]?.jsonPrimitive?.content)
        assertEquals("https://issuer.example/nonce", jsonObject["nonce_endpoint"]?.jsonPrimitive?.content)
    }

    // 4) Encryption blocks: credential_request_encryption + credential_response_encryption
    @Test
    fun `serializes credential request and response encryption`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialRequestEncryption = CredentialRequestEncryption(
                jwks = jwksWithKid("key-1"),
                encValuesSupported = setOf("A128GCM"),
                zipValuesSupported = setOf("DEF"),
                encryptionRequired = true,
            ),
            credentialResponseEncryption = CredentialResponseEncryption(
                algValuesSupported = setOf("ECDH-ES"),
                encValuesSupported = setOf("A128GCM"),
                zipValuesSupported = setOf("DEF"),
                encryptionRequired = true,
            ),
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(id = "cred-id-1", format = CredentialFormat.SD_JWT_VC),
            ),
        )

        val jsonObject = json.parseToJsonElement(json.encodeToString(metadata)).jsonObject
        val requestEncryption = jsonObject["credential_request_encryption"]?.jsonObject
        val responseEncryption = jsonObject["credential_response_encryption"]?.jsonObject

        assertNotNull(requestEncryption)
        assertNotNull(responseEncryption)

        val requestJwks = requestEncryption["jwks"]?.jsonObject
        val requestKeys = requestJwks?.get("keys")?.jsonArray
        assertNotNull(requestJwks)
        assertNotNull(requestKeys)
        assertEquals("key-1", requestKeys.first().jsonObject["kid"]?.jsonPrimitive?.content)

        assertEquals("A128GCM", requestEncryption["enc_values_supported"]?.jsonArray?.first()?.jsonPrimitive?.content)
        assertEquals("DEF", requestEncryption["zip_values_supported"]?.jsonArray?.first()?.jsonPrimitive?.content)
        assertEquals("ECDH-ES", responseEncryption["alg_values_supported"]?.jsonArray?.first()?.jsonPrimitive?.content)
        assertEquals("DEF", responseEncryption["zip_values_supported"]?.jsonArray?.first()?.jsonPrimitive?.content)
    }

    // 5) batch_credential_issuance
    @Test
    fun `serializes batch credential issuance`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            batchCredentialIssuance = BatchCredentialIssuance(batchSize = 10),
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(id = "cred-id-1", format = CredentialFormat.SD_JWT_VC),
            ),
        )

        val jsonObject = json.parseToJsonElement(json.encodeToString(metadata)).jsonObject
        val batch = jsonObject["batch_credential_issuance"]?.jsonObject

        assertNotNull(batch)
        assertEquals(10, batch["batch_size"]?.jsonPrimitive?.content?.toInt())
    }

    // 6) display array
    @Test
    fun `serializes display entries`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            display = listOf(
                IssuerDisplay(
                    name = "Issuer",
                    locale = "en",
                    logo = IssuerLogo(uri = "https://issuer.example/logo.png"),
                ),
            ),
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(id = "cred-id-1", format = CredentialFormat.SD_JWT_VC),
            ),
        )

        val jsonObject = json.parseToJsonElement(json.encodeToString(metadata)).jsonObject
        val display = jsonObject["display"] as? JsonArray

        assertNotNull(display)
        assertEquals("Issuer", display[0].jsonObject["name"]?.jsonPrimitive?.content)
        assertEquals("en", display[0].jsonObject["locale"]?.jsonPrimitive?.content)
        assertEquals(
            "https://issuer.example/logo.png",
            display[0].jsonObject["logo"]?.jsonObject?.get("uri")?.jsonPrimitive?.content,
        )
    }

    // 7) credential_configurations_supported object with format-specific fields
    @Test
    fun `serializes credential configurations with format specific fields`() {
        val configurationJwt = CredentialConfiguration(
            id = "cred-jwt",
            format = CredentialFormat.JWT_VC_JSON,
            credentialDefinition = CredentialDefinition(
                type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
            ),
            credentialSigningAlgValuesSupported = setOf(
                SigningAlgId.Jose("ES256"),
            ),
            cryptographicBindingMethodsSupported = setOf(
                CryptographicBindingMethod.Jwk,
            ),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to
                    ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
            ),
            credentialMetadata = CredentialMetadata(
                display = listOf(
                    CredentialDisplay(name = "University Credential", locale = "en-US"),
                ),
                claims = listOf(
                    ClaimDescription(path = listOf("credentialSubject", "given_name")),
                ),
            ),
        )
        val configurationMdoc = CredentialConfiguration(
            id = "cred-mdoc",
            format = CredentialFormat.MSO_MDOC,
            doctype = "org.iso.18013.5.1.mDL",
            credentialSigningAlgValuesSupported = setOf(
                SigningAlgId.CoseValue(-7),
                SigningAlgId.CoseValue(-9),
            ),
            cryptographicBindingMethodsSupported = setOf(
                CryptographicBindingMethod.CoseKey,
            ),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to
                    ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
            ),
            credentialMetadata = CredentialMetadata(
                display = listOf(
                    CredentialDisplay(name = "Mobile Driving License", locale = "en-US"),
                ),
                claims = listOf(
                    ClaimDescription(path = listOf("org.iso.18013.5.1", "given_name")),
                    ClaimDescription(path = listOf("org.iso.18013.5.1", "family_name")),
                ),
            ),
        )
        val configurationSdJwt = CredentialConfiguration(
            id = "cred-sd",
            format = CredentialFormat.SD_JWT_VC,
            vct = "SD_JWT_VC_example_in_OpenID4VCI",
            cryptographicBindingMethodsSupported = setOf(
                CryptographicBindingMethod.Jwk,
            ),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to
                    ProofType(
                        proofSigningAlgValuesSupported = setOf("ES256"),
                        keyAttestationsRequired = KeyAttestationsRequired(
                            keyStorage = setOf("iso_18045_moderate"),
                            userAuthentication = setOf("iso_18045_moderate"),
                        ),
                    ),
            ),
        )

        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = mapOf(
                "cred-jwt" to configurationJwt,
                "cred-mdoc" to configurationMdoc,
                "cred-sd" to configurationSdJwt,
            ),
        )

        val jsonObject = json.parseToJsonElement(json.encodeToString(metadata)).jsonObject
        val configurations = jsonObject["credential_configurations_supported"]?.jsonObject

        assertNotNull(configurations)

        val jwtConfig = configurations["cred-jwt"]?.jsonObject
        val mdocConfig = configurations["cred-mdoc"]?.jsonObject
        val sdConfig = configurations["cred-sd"]?.jsonObject

        assertEquals(CredentialFormat.JWT_VC_JSON.value, jwtConfig?.get("format")?.jsonPrimitive?.content)
        assertEquals(CredentialFormat.MSO_MDOC.value, mdocConfig?.get("format")?.jsonPrimitive?.content)
        assertEquals(CredentialFormat.SD_JWT_VC.value, sdConfig?.get("format")?.jsonPrimitive?.content)

        assertEquals(
            "UniversityDegreeCredential",
            jwtConfig?.get("credential_definition")
                ?.jsonObject
                ?.get("type")
                ?.jsonArray
                ?.last()
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "ES256",
            jwtConfig?.get("credential_signing_alg_values_supported")
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "jwk",
            jwtConfig?.get("cryptographic_binding_methods_supported")
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "jwt",
            jwtConfig?.get("proof_types_supported")
                ?.jsonObject
                ?.keys
                ?.firstOrNull(),
        )
        assertEquals(
            "University Credential",
            jwtConfig?.get("credential_metadata")
                ?.jsonObject
                ?.get("display")
                ?.jsonArray
                ?.first()
                ?.jsonObject
                ?.get("name")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "credentialSubject",
            jwtConfig?.get("credential_metadata")
                ?.jsonObject
                ?.get("claims")
                ?.jsonArray
                ?.first()
                ?.jsonObject
                ?.get("path")
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("org.iso.18013.5.1.mDL", mdocConfig?.get("doctype")?.jsonPrimitive?.content)
        assertEquals(
            -7,
            mdocConfig?.get("credential_signing_alg_values_supported")
                ?.jsonArray
                ?.get(0)
                ?.jsonPrimitive
                ?.content
                ?.toInt(),
        )
        assertEquals(
            -9,
            mdocConfig?.get("credential_signing_alg_values_supported")
                ?.jsonArray
                ?.get(1)
                ?.jsonPrimitive
                ?.content
                ?.toInt(),
        )
        assertEquals(
            "cose_key",
            mdocConfig?.get("cryptographic_binding_methods_supported")
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals(
            "org.iso.18013.5.1",
            mdocConfig?.get("credential_metadata")
                ?.jsonObject
                ?.get("claims")
                ?.jsonArray
                ?.first()
                ?.jsonObject
                ?.get("path")
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("SD_JWT_VC_example_in_OpenID4VCI", sdConfig?.get("vct")?.jsonPrimitive?.content)
        assertEquals(
            "iso_18045_moderate",
            sdConfig?.get("proof_types_supported")
                ?.jsonObject
                ?.get("jwt")
                ?.jsonObject
                ?.get("key_attestations_required")
                ?.jsonObject
                ?.get("key_storage")
                ?.jsonArray
                ?.first()
                ?.jsonPrimitive
                ?.content,
        )
    }

    // 8) null collections should be omitted
    @Test
    fun `omits null collections from json output`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(id = "cred-id-1", format = CredentialFormat.SD_JWT_VC),
            ),
            authorizationServers = null,
            display = null,
        )

        val encoded = json.encodeToString(metadata)

        assertFalse(encoded.contains("authorization_servers"))
        assertFalse(encoded.contains("display"))
    }

    private fun jwksWithKid(kid: String): JsonObject =
        buildJsonObject {
            put(
                "keys",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("kty", JsonPrimitive("EC"))
                            put("kid", JsonPrimitive(kid))
                        }
                    )
                )
            )
        }
}
