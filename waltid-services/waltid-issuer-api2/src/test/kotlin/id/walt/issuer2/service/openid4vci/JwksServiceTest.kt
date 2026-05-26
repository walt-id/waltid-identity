package id.walt.issuer2.service.openid4vci

import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.config.Issuer2ServiceConfig
import id.walt.issuer2.service.CredentialProfileService
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.fail

class JwksServiceTest {

    @Test
    fun `lists token and profile public jwks with deduplication`() = runTest {
        val tokenSigningKey = JWKKey.generate(KeyType.secp256r1)
        val profileIssuerKey = JWKKey.generate(KeyType.secp256r1)
        val service = jwksService(
            tokenSigningKey = tokenSigningKey,
            profileIssuerKeys = listOf(profileIssuerKey, profileIssuerKey),
        )

        val jwks = service.listJwks()

        val keys = jwks["keys"]?.jsonArray ?: fail("Expected JWKS keys array")
        assertEquals(2, keys.size)
        assertEquals(
            setOf(tokenSigningKey.getKeyId(), profileIssuerKey.getKeyId()),
            keys.map { it.jsonObject["kid"]?.jsonPrimitive?.content }.toSet(),
        )
        keys.forEach { key ->
            assertFalse("d" in key.jsonObject, "JWKS must expose public keys only")
        }
    }

    private fun jwksService(
        tokenSigningKey: JWKKey,
        profileIssuerKeys: List<JWKKey>,
    ): JwksService =
        JwksService(
            serviceConfig = Issuer2ServiceConfig(
                baseUrl = "http://localhost:7003",
                ciTokenKey = KeySerialization.serializeKey(tokenSigningKey),
            ),
            profileService = CredentialProfileService(
                profilesConfig = Issuer2ProfilesConfig(
                    profiles = profileIssuerKeys.mapIndexed { index, key ->
                        "profile$index" to profileConfig(key)
                    }.toMap()
                ),
                metadataConfig = Issuer2MetadataConfig(
                    credentialConfigurations = mapOf(
                        CREDENTIAL_CONFIGURATION_ID to buildJsonObject {
                            put("format", "jwt_vc_json")
                        }
                    )
                ),
            ),
        )

    private fun profileConfig(issuerKey: JWKKey): CredentialProfileConfig =
        CredentialProfileConfig(
            name = "University Degree",
            credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
            issuerKey = Json.parseToJsonElement(KeySerialization.serializeKey(issuerKey)).jsonObject,
            credentialData = buildJsonObject {},
        )

    private companion object {
        const val CREDENTIAL_CONFIGURATION_ID = "UniversityDegree_jwt_vc_json"
    }
}
