package id.walt.openid4vci.requests.authorization

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorizationDetailCompatibilityTest {
    @Test
    fun releasedConstructionAndCopyPreserveLocationsAndWireNames() {
        val released = AuthorizationDetail(OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE, "identity")
        val located = released.copy(locations = listOf("https://issuer.example"))
        val updated = located.copy(credentialIdentifiers = listOf("dataset"))
        assertEquals(located.locations, updated.locations)
        assertEquals(updated, updated.copy())
        val encoded = Json.encodeToString(updated)
        assertEquals(
            """{"type":"openid_credential","credential_configuration_id":"identity","credential_identifiers":["dataset"],"locations":["https://issuer.example"]}""",
            encoded,
        )
        assertEquals(updated, Json.decodeFromString<AuthorizationDetail>(encoded))
        assertEquals(released, Json.decodeFromString<AuthorizationDetail>(
            """{"type":"openid_credential","credential_configuration_id":"identity"}"""
        ))
    }
}
