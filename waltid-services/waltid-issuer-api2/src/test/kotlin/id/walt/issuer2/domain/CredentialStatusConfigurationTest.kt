package id.walt.issuer2.domain

import id.walt.openid4vci.CredentialFormat
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class CredentialStatusConfigurationTest {
    @Test
    fun fixedObjectAndFlatW3cArrayKeepTheirMeaning() {
        val value = w3cStatus(1)
        for (fixed in listOf(value, JsonArray(listOf(value, w3cStatus(2, "suspension"))))) {
            assertEquals(fixed, assertIs<CredentialStatusConfiguration.Fixed>(
                CredentialStatusConfiguration.parse(fixed, CredentialFormat.JWT_VC_JSON),
            ).value)
        }
    }

    @Test
    fun flatTokenArraysSupplyIndividualValuesForSdJwtAndMdoc() {
        for (format in listOf(CredentialFormat.SD_JWT_VC, CredentialFormat.MSO_MDOC)) {
            val configuration = assertIs<CredentialStatusConfiguration.Preallocated>(
                CredentialStatusConfiguration.parse(tokenGroups(1, 2), format),
            )
            assertEquals(listOf(tokenStatus(1), tokenStatus(2)), configuration.groups.map {
                CredentialStatusConfiguration.credentialValue(it.statuses, format)
            })
            assertEquals(configuration, CredentialStatusConfiguration.parse(
                JsonArray(configuration.groups.map { it.statuses }), format,
            ), "Prepared grouped snapshots retain the same assignments")
        }
    }

    @Test
    fun perCopyEntriesCanUseTheSameIndexOnDifferentLists() {
        val first = tokenStatus(1)
        val second = buildJsonObject {
            putJsonObject("status_list") { put("idx", 1); put("uri", "https://status.example.com/2") }
        }
        for (format in listOf(CredentialFormat.SD_JWT_VC, CredentialFormat.MSO_MDOC)) {
            val configuration = assertIs<CredentialStatusConfiguration.Preallocated>(
                CredentialStatusConfiguration.parse(JsonArray(listOf(first, second)), format),
            )
            assertEquals(listOf(first, second), configuration.groups.map {
                CredentialStatusConfiguration.credentialValue(it.statuses, format)
            })
            assertEquals(2, configuration.groups.flatMap { it.references }.distinct().size)
        }
    }

    @Test
    fun w3cGroupKeepsBothStatusPurposesTogether() {
        val group = JsonArray(listOf(w3cStatus(1), w3cStatus(7, "suspension")))
        val configuration = assertIs<CredentialStatusConfiguration.Preallocated>(
            CredentialStatusConfiguration.parse(JsonArray(listOf(group)), CredentialFormat.JWT_VC_JSON),
        )
        assertEquals(group, CredentialStatusConfiguration.credentialValue(
            configuration.groups.single().statuses, CredentialFormat.JWT_VC_JSON,
        ))
        assertEquals(4, configuration.groups.single().references.size, "Both URI/index pairs and entry IDs are tracked")
    }

    @Test
    fun malformedAndAmbiguousGroupsAreRejected() {
        val good = tokenStatus(1)
        for (value in listOf(
            JsonArray(emptyList()),
            JsonArray(listOf(JsonPrimitive("not a status"))),
            JsonArray(listOf(good, JsonArray(listOf(tokenStatus(2))))),
            JsonArray(listOf(JsonArray(emptyList()))),
            JsonArray(listOf(JsonArray(listOf(JsonArray(listOf(good)))))),
            JsonArray(listOf(JsonArray(listOf(good, tokenStatus(2))))),
            tokenGroups(1, 1),
            JsonArray(listOf(JsonArray(listOf(JsonPrimitive("not a status"))))),
        )) assertFailsWith<IllegalArgumentException> {
            CredentialStatusConfiguration.parse(value, CredentialFormat.SD_JWT_VC)
        }
    }

    @Test
    fun invalidTokenReferencesAreRejected() {
        for (index in listOf(JsonPrimitive(-1), JsonPrimitive("1"), JsonPrimitive(1.5))) {
            val value = JsonArray(listOf(buildJsonObject {
                putJsonObject("status_list") { put("idx", index); put("uri", "https://status.example.com/1") }
            }))
            assertFailsWith<IllegalArgumentException> {
                CredentialStatusConfiguration.parse(value, CredentialFormat.SD_JWT_VC)
            }
        }
    }

    @Test
    fun w3cIndexesAreComparedByTheirNumericValue() {
        val first = w3cStatus(1)
        val second = JsonObject(first + ("statusListIndex" to JsonPrimitive("0001")) - "id")
        assertFailsWith<IllegalArgumentException> {
            CredentialStatusConfiguration.parse(
                JsonArray(listOf(JsonArray(listOf(first)), JsonArray(listOf(second)))),
                CredentialFormat.JWT_VC_JSON,
            )
        }
    }

    @Test
    fun statusMappingsCannotReplaceReservationsButBusinessStatusClaimsAreAllowed() {
        for (path in listOf("status", "$.status", "jwt:status", "status.status_list.idx")) {
            assertFailsWith<IllegalArgumentException> {
                CredentialStatusConfiguration.parse(tokenGroups(1), CredentialFormat.SD_JWT_VC,
                    buildJsonObject { put(path, "overwrite") })
            }
        }
        CredentialStatusConfiguration.parse(tokenGroups(1), CredentialFormat.SD_JWT_VC,
            buildJsonObject { put("credentialSubject.status", "active") })
        assertFailsWith<IllegalArgumentException> {
            CredentialStatusConfiguration.parse(JsonArray(listOf(JsonArray(listOf(w3cStatus(1))))),
                CredentialFormat.JWT_VC_JSON, buildJsonObject { put("vc.credentialStatus", "overwrite") })
        }
    }

    companion object {
        fun tokenStatus(index: Int): JsonObject = buildJsonObject {
            putJsonObject("status_list") { put("idx", index); put("uri", "https://status.example.com/1") }
        }

        fun tokenGroups(vararg indexes: Int) = JsonArray(indexes.map(::tokenStatus))

        fun w3cStatus(index: Int, purpose: String = "revocation"): JsonObject = buildJsonObject {
            put("type", "BitstringStatusListEntry")
            put("statusPurpose", purpose)
            put("statusListIndex", index.toString())
            put("statusListCredential", "https://status.example.com/" + purpose)
            put("id", "https://status.example.com/" + purpose + "#" + index)
        }
    }
}
