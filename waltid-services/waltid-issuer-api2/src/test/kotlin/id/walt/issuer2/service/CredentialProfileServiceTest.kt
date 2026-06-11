package id.walt.issuer2.service

import id.walt.issuer2.config.CredentialProfileConfig
import id.walt.issuer2.config.Issuer2MetadataConfig
import id.walt.issuer2.config.Issuer2ProfilesConfig
import id.walt.issuer2.notifications.IssuanceNotifications
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.sdjwt.SDField
import id.walt.sdjwt.SDMap
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class CredentialProfileServiceTest {

    @Test
    fun `lists config-backed profiles with all profile fields mapped`() {
        val service = serviceWithProfiles(
            profiles = mapOf(
                PROFILE_ID to profileConfig(
                    issuerDid = "did:web:issuer.example",
                    mapping = buildJsonObject {
                        put("id", "<uuid>")
                    },
                    selectiveDisclosure = SDMap(
                        mapOf("credentialSubject" to SDField(sd = true))
                    ),
                    idTokenClaimsMapping = mapOf(
                        "sub" to "$.credentialSubject.id",
                        "given_name" to "$.credentialSubject.givenName",
                    ),
                    mDocNameSpacesDataMappingConfig = mapOf(
                        "org.iso.18013.5.1" to JsonObjectToCborMappingConfig(emptyMap()),
                    ),
                    x5Chain = listOf("-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----"),
                    notifications = IssuanceNotifications(
                        webhook = IssuanceNotifications.WebhookNotification(
                            url = "https://issuer.example/webhook",
                        ),
                    ),
                )
            )
        )

        val profile = service.listProfiles().single()

        assertEquals(PROFILE_ID, profile.profileId)
        assertEquals("University Degree", profile.name)
        assertEquals(CREDENTIAL_CONFIGURATION_ID, profile.credentialConfigurationId)
        assertEquals("jwk", profile.issuerKey["type"]?.jsonPrimitive?.content)
        assertEquals(DEFAULT_ISSUER_KEY_D, profile.issuerKey["jwk"]?.jsonObject?.get("d")?.jsonPrimitive?.content)
        assertEquals("did:web:issuer.example", profile.issuerDid)
        assertEquals("UniversityDegreeCredential", profile.credentialData["type"]?.jsonPrimitive?.content)
        assertNotNull(profile.mapping)
        assertNotNull(profile.selectiveDisclosure)
        assertEquals("$.credentialSubject.id", profile.idTokenClaimsMapping?.get("sub"))
        assertEquals(setOf("org.iso.18013.5.1"), profile.mDocNameSpacesDataMappingConfig?.keys)
        assertEquals(listOf("-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----"), profile.x5Chain)
        assertEquals("https://issuer.example/webhook", profile.notifications?.webhook?.url)
    }

    @Test
    fun `gets one profile by id`() {
        val service = serviceWithProfiles(
            profiles = mapOf(
                PROFILE_ID to profileConfig(),
                "otherProfile" to profileConfig(name = "Other Profile"),
            )
        )

        val profile = service.getProfile(PROFILE_ID)

        assertEquals(PROFILE_ID, profile.profileId)
        assertEquals("University Degree", profile.name)
    }

    @Test
    fun `throws not found for unknown profile`() {
        val service = serviceWithProfiles(profiles = mapOf(PROFILE_ID to profileConfig()))

        assertFailsWith<NotFoundException> {
            service.getProfile("missing")
        }
    }

    @Test
    fun `rejects profile referencing unsupported credential configuration`() {
        val service = serviceWithProfiles(
            profiles = mapOf(
                PROFILE_ID to profileConfig(credentialConfigurationId = "unsupported")
            )
        )

        assertFailsWith<IllegalArgumentException> {
            service.resolveProfile(PROFILE_ID)
        }
    }

    @Test
    fun `rejects invalid profile values`() {
        val service = serviceWithProfiles(
            profiles = mapOf(
                "invalid.profile" to profileConfig()
            )
        )

        assertFailsWith<IllegalArgumentException> {
            service.listProfiles()
        }
    }

    private fun serviceWithProfiles(
        profiles: Map<String, CredentialProfileConfig>,
        metadataConfig: Issuer2MetadataConfig = metadataConfig(),
    ): CredentialProfileService =
        CredentialProfileService(
            profilesConfig = Issuer2ProfilesConfig(profiles = profiles),
            metadataConfig = metadataConfig,
        )

    private fun profileConfig(
        name: String = "University Degree",
        credentialConfigurationId: String = CREDENTIAL_CONFIGURATION_ID,
        issuerKey: JsonObject = defaultIssuerKey(),
        issuerDid: String? = null,
        credentialData: JsonObject = buildJsonObject {
            put("type", "UniversityDegreeCredential")
            putJsonObject("credentialSubject") {
                put("id", "did:example:subject")
                put("givenName", "Jane")
            }
        },
        mapping: JsonObject? = null,
        selectiveDisclosure: SDMap? = null,
        idTokenClaimsMapping: Map<String, String>? = null,
        mDocNameSpacesDataMappingConfig: Map<String, JsonObjectToCborMappingConfig>? = null,
        x5Chain: List<String>? = null,
        notifications: IssuanceNotifications? = null,
    ): CredentialProfileConfig =
        CredentialProfileConfig(
            name = name,
            credentialConfigurationId = credentialConfigurationId,
            issuerKey = issuerKey,
            issuerDid = issuerDid,
            credentialData = credentialData,
            mapping = mapping,
            selectiveDisclosure = selectiveDisclosure,
            idTokenClaimsMapping = idTokenClaimsMapping,
            mDocNameSpacesDataMappingConfig = mDocNameSpacesDataMappingConfig,
            x5Chain = x5Chain,
            notifications = notifications,
        )

    private fun metadataConfig(): Issuer2MetadataConfig =
        Issuer2MetadataConfig(
            credentialConfigurations = mapOf(
                CREDENTIAL_CONFIGURATION_ID to buildJsonObject {
                    put("format", "jwt_vc_json")
                }
            )
        )

    private fun defaultIssuerKey(): JsonObject = buildJsonObject {
        put("type", "jwk")
        put("jwk", buildJsonObject {
            put("kty", "EC")
            put("d", DEFAULT_ISSUER_KEY_D)
            put("crv", "P-256")
            put("x", "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM")
            put("y", "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E")
        })
    }

    private companion object {
        const val PROFILE_ID = "universityDegree"
        const val CREDENTIAL_CONFIGURATION_ID = "UniversityDegree_jwt_vc_json"
        const val DEFAULT_ISSUER_KEY_D = "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ"
    }
}
