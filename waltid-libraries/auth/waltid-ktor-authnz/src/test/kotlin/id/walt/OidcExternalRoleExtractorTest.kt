package id.walt

import id.walt.ktorauthnz.methods.OIDC
import id.walt.ktorauthnz.methods.OidcExternalRoleExtractor
import id.walt.ktorauthnz.methods.config.OidcAuthConfiguration
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OidcExternalRoleExtractorTest {

    private val baseConfig = OidcAuthConfiguration(
        openIdConfiguration = OIDC.OpenIdConfiguration(
            issuer = "https://issuer",
            authorizationEndpoint = "https://issuer/auth",
            tokenEndpoint = "https://issuer/token",
            jwksUri = "https://issuer/jwks",
        ),
        clientId = "waltid_ktor_authnz",
        clientSecret = "secret",
        callbackUri = "http://localhost:8080/auth/account/oidc/callback",
    )

    @Test
    fun `returns null when extraction is disabled`() {
        val result = OidcExternalRoleExtractor.extract(
            idTokenPayload = buildJsonObject { },
            config = baseConfig,
            issuer = "https://issuer",
            subject = "subject",
        )

        assertNull(result)
    }

    @Test
    fun `extracts realm and configured client roles`() {
        val config = baseConfig.copy(
            externalRoleExtraction = baseConfig.externalRoleExtraction.copy(enabled = true)
        )

        val payload = buildJsonObject {
            putJsonObject("realm_access") {
                putJsonArray("roles") {
                    add(kotlinx.serialization.json.JsonPrimitive("tenant-admin"))
                    add(kotlinx.serialization.json.JsonPrimitive("auditor"))
                }
            }
            putJsonObject("resource_access") {
                putJsonObject("waltid_ktor_authnz") {
                    putJsonArray("roles") {
                        add(kotlinx.serialization.json.JsonPrimitive("wallet-operator"))
                        add(kotlinx.serialization.json.JsonPrimitive("wallet-reader"))
                    }
                }
                putJsonObject("other-client") {
                    putJsonArray("roles") {
                        add(kotlinx.serialization.json.JsonPrimitive("should-not-be-included"))
                    }
                }
            }
        }

        val result = OidcExternalRoleExtractor.extract(payload, config, "https://issuer", "subject")

        assertNotNull(result)
        assertEquals(setOf("tenant-admin", "auditor"), result.realmRoles)
        assertEquals(setOf("wallet-operator", "wallet-reader"), result.clientRoles["waltid_ktor_authnz"])
        assertEquals(1, result.clientRoles.size)
    }

    @Test
    fun `supports custom claim paths and client id override`() {
        val config = baseConfig.copy(
            externalRoleExtraction = baseConfig.externalRoleExtraction.copy(
                enabled = true,
                realmRolesClaimPath = "custom.realm.roles",
                clientRolesClaimPath = "custom.clients",
                clientId = "enterprise-api",
            )
        )

        val payload = buildJsonObject {
            putJsonObject("custom") {
                putJsonObject("realm") {
                    putJsonArray("roles") { add(kotlinx.serialization.json.JsonPrimitive("r1")) }
                }
                putJsonObject("clients") {
                    putJsonObject("enterprise-api") {
                        putJsonArray("roles") { add(kotlinx.serialization.json.JsonPrimitive("c1")) }
                    }
                }
            }
        }

        val result = OidcExternalRoleExtractor.extract(payload, config, "https://issuer", "subject")

        assertNotNull(result)
        assertEquals(setOf("r1"), result.realmRoles)
        assertEquals(setOf("c1"), result.clientRoles["enterprise-api"])
    }

    @Test
    fun `gracefully handles missing or malformed role claims`() {
        val config = baseConfig.copy(
            externalRoleExtraction = baseConfig.externalRoleExtraction.copy(enabled = true)
        )

        val payload = buildJsonObject {
            put("realm_access", "invalid")
            putJsonObject("resource_access") {
                putJsonObject("waltid_ktor_authnz") {
                    put("roles", "invalid")
                }
            }
        }

        val result = OidcExternalRoleExtractor.extract(payload, config, "https://issuer", "subject")

        assertNotNull(result)
        assertEquals(emptySet(), result.realmRoles)
        assertEquals(emptyMap(), result.clientRoles)
    }
}
