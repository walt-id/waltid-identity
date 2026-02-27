package id.walt.openid4vci.metadata.issuer

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.prooftypes.ProofTypeId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CredentialIssuerMetadataTest {

    @Test
    fun `credential issuer must not include query or fragment`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example?x=1",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example#frag",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
            )
        }
    }

    // credential_endpoint can be any scheme; validation only ensures host is present

    @Test
    fun `credential configurations supported must not be empty`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = emptyMap(),
            )
        }
    }

    @Test
    fun `authorization servers must be non-empty and without query or fragment when provided`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                authorizationServers = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                authorizationServers = listOf("https://auth.example?x=1"),
            )
        }
    }

    @Test
    fun `credential request encryption validates jwks and enc values`() {
        val jwks = buildJsonObject {
            put(
                "keys",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("kty", JsonPrimitive("EC"))
                            put("kid", JsonPrimitive("key-1"))
                        }
                    )
                )
            )
        }

        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                credentialRequestEncryption = CredentialRequestEncryption(
                    jwks = jwks,
                    encValuesSupported = emptySet(),
                    encryptionRequired = true,
                ),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            val jwksWithoutKid = buildJsonObject {
                put(
                    "keys",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("kty", JsonPrimitive("EC"))
                            }
                        )
                    )
                )
            }
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                credentialRequestEncryption = CredentialRequestEncryption(
                    jwks = jwksWithoutKid,
                    encValuesSupported = setOf("A128GCM"),
                    encryptionRequired = true,
                ),
            )
        }
    }

    @Test
    fun `credential response encryption validates alg and enc values`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                credentialResponseEncryption = CredentialResponseEncryption(
                    algValuesSupported = emptySet(),
                    encValuesSupported = setOf("A128GCM"),
                    encryptionRequired = true,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                credentialResponseEncryption = CredentialResponseEncryption(
                    algValuesSupported = setOf("RSA-OAEP-256"),
                    encValuesSupported = emptySet(),
                    encryptionRequired = true,
                ),
            )
        }
    }

    @Test
    fun `credential request and response encryption accept full examples`() {
        val jwks = buildJsonObject {
            put(
                "keys",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("kty", JsonPrimitive("EC"))
                            put("kid", JsonPrimitive("ac"))
                            put("use", JsonPrimitive("enc"))
                            put("crv", JsonPrimitive("P-256"))
                            put("alg", JsonPrimitive("ECDH-ES"))
                            put("x", JsonPrimitive("YO4epjifD-KWeq1sL2tNmm36BhXnkJ0He-WqMYrp9Fk"))
                            put("y", JsonPrimitive("Hekpm0zfK7C-YccH5iBjcIXgf6YdUvNUac_0At55Okk"))
                        }
                    )
                )
            )
        }

        CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
            ),
            credentialRequestEncryption = CredentialRequestEncryption(
                jwks = jwks,
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
        )
    }

    @Test
    fun `batch credential issuance requires batch size of at least two`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                batchCredentialIssuance = BatchCredentialIssuance(batchSize = 1),
            )
        }
    }

    @Test
    fun `accepts batch credential issuance example`() {
        CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
            ),
            batchCredentialIssuance = BatchCredentialIssuance(batchSize = 10),
        )
    }

    @Test
    fun `display requires unique locales and non-empty entries`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                display = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
                display = listOf(
                    IssuerDisplay(name = "Issuer", locale = "en"),
                    IssuerDisplay(name = "Issuer DE", locale = "en"),
                ),
            )
        }
    }

    @Test
    fun `accepts display example`() {
        CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
            ),
            display = listOf(
                IssuerDisplay(
                    name = "Issuer",
                    locale = "en",
                    logo = IssuerLogo(
                        uri = "https://issuer.example/logo.png",
                        altText = "issuer logo",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `authorization servers default to credential issuer`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
            ),
        )

        assertEquals(listOf("https://issuer.example"), metadata.authorizationServerIssuers())
        assertEquals(true, metadata.isAuthorizationServerDeclared(null))
    }

    @Test
    fun `issuer can declare a separate authorization server`() {
        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(
                    format = CredentialFormat.SD_JWT_VC
                ),
            ),
            authorizationServers = listOf("https://auth.example"),
        )

        assertEquals(listOf("https://auth.example"), metadata.authorizationServerIssuers())
        assertEquals(true, metadata.isAuthorizationServerDeclared("https://auth.example"))
        assertEquals(false, metadata.isAuthorizationServerDeclared("https://other.example"))
    }

    @Test
    fun `accepts full issuer metadata with authorization servers`() {
        val jwks = buildJsonObject {
            put(
                "keys",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("kty", JsonPrimitive("EC"))
                            put("kid", JsonPrimitive("key-1"))
                        }
                    )
                )
            )
        }

        val configuration = CredentialConfiguration(
            format = CredentialFormat.JWT_VC_JSON,
            scope = "UniversityDegree",
            credentialDefinition = CredentialDefinition(
                type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
            ),
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
            ),
            credentialMetadata = CredentialMetadata(
                display = listOf(
                    CredentialDisplay(
                        name = "IdentityCredential",
                        locale = "en-US",
                    ),
                ),
            ),
        )

        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            authorizationServers = listOf("https://auth.example"),
            credentialEndpoint = "https://issuer.example/credential",
            deferredCredentialEndpoint = "https://issuer.example/credential_deferred",
            notificationEndpoint = "https://issuer.example/notification",
            nonceEndpoint = "https://issuer.example/nonce",
            credentialRequestEncryption = CredentialRequestEncryption(
                jwks = jwks,
                encValuesSupported = setOf("A128GCM"),
                encryptionRequired = true,
            ),
            credentialResponseEncryption = CredentialResponseEncryption(
                algValuesSupported = setOf("ECDH-ES"),
                encValuesSupported = setOf("A128GCM"),
                encryptionRequired = true,
            ),
            batchCredentialIssuance = BatchCredentialIssuance(batchSize = 10),
            display = listOf(
                IssuerDisplay(
                    name = "Issuer",
                    locale = "en",
                    logo = IssuerLogo(uri = "https://issuer.example/logo.png"),
                ),
            ),
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to configuration,
            ),
        )

        assertEquals(listOf("https://auth.example"), metadata.authorizationServerIssuers())
        assertEquals("https://issuer.example/credential", metadata.credentialEndpoint)
    }

    @Test
    fun `accepts full issuer metadata without authorization servers`() {
        val jwks = buildJsonObject {
            put(
                "keys",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("kty", JsonPrimitive("EC"))
                            put("kid", JsonPrimitive("key-1"))
                        }
                    )
                )
            )
        }

        val configuration = CredentialConfiguration(
            format = CredentialFormat.MSO_MDOC,
            doctype = "org.iso.18013.5.1.mDL",
        )

        val metadata = CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            credentialRequestEncryption = CredentialRequestEncryption(
                jwks = jwks,
                encValuesSupported = setOf("A128GCM"),
                encryptionRequired = false,
            ),
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to configuration,
            ),
        )

        assertEquals(listOf("https://issuer.example"), metadata.authorizationServerIssuers())
        assertEquals(true, metadata.isAuthorizationServerDeclared(null))
    }

    @Test
    fun `fromBaseUrl supports full credential configurations`() {
        val configurationJwt = CredentialConfiguration(
            format = CredentialFormat.JWT_VC_JSON,
            scope = "UniversityDegree",
            credentialDefinition = CredentialDefinition(
                type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
            ),
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
            ),
        )
        val configurationSdJwt = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            scope = "SD_JWT_VC_example_in_OpenID4VCI",
            vct = "SD_JWT_VC_example_in_OpenID4VCI",
        )
        val configurationMdoc = CredentialConfiguration(
            format = CredentialFormat.MSO_MDOC,
            doctype = "org.iso.18013.5.1.mDL",
        )

        val issuerMetadata = CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = "https://issuer.example",
            credentialConfigurationsSupported = mapOf(
                "cred-jwt" to configurationJwt,
                "cred-sd" to configurationSdJwt,
                "cred-mdoc" to configurationMdoc,
            ),
            authorizationServers = listOf("https://auth.example"),
        )

        assertEquals("https://issuer.example/credential", issuerMetadata.credentialEndpoint)
        assertEquals("https://issuer.example/nonce", issuerMetadata.nonceEndpoint)
        assertEquals(listOf("https://auth.example"), issuerMetadata.authorizationServerIssuers())
        assertEquals(configurationJwt, issuerMetadata.credentialConfigurationsSupported.getValue("cred-jwt"))
        assertEquals(configurationSdJwt, issuerMetadata.credentialConfigurationsSupported.getValue("cred-sd"))
        assertEquals(configurationMdoc, issuerMetadata.credentialConfigurationsSupported.getValue("cred-mdoc"))
    }

    @Test
    fun `default metadata builders produce expected endpoints`() {
        val issuerMetadata = CredentialIssuerMetadata.fromBaseUrl(
            baseUrl = "https://issuer.example",
            credentialConfigurationsSupported = mapOf(
                "cred-id-1" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
            ),
        )
        assertEquals("https://issuer.example/credential", issuerMetadata.credentialEndpoint)
        assertEquals("https://issuer.example/nonce", issuerMetadata.nonceEndpoint)

        val asMetadata = AuthorizationServerMetadata.fromBaseUrl("https://issuer.example")
        assertEquals("https://issuer.example/authorize", asMetadata.authorizationEndpoint)
        assertEquals("https://issuer.example/token", asMetadata.tokenEndpoint)
        assertEquals("https://issuer.example/jwks", asMetadata.jwksUri)
    }
}
