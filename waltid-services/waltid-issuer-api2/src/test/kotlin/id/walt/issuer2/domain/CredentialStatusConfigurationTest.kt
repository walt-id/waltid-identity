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
    fun statusListUriAliasesAreDuplicatesWithinOneInventory() {
        val host = "https://status.example.com"
        for ((path, aliasPath) in listOf(
            "/list" to "/unused/../list",
            "/list" to "/unused/%2e%2E/%6cist",
            "/list" to "/./list",
            "/list?scope=a" to "/list?scope=%61",
            "/list?scope=%2F" to "/list?scope=%2f",
        )) {
            val url = host + path
            val alias = host + aliasPath
            for (format in listOf(CredentialFormat.SD_JWT_VC, CredentialFormat.MSO_MDOC, CredentialFormat.JWT_VC_JSON)) {
                val inventory = if (format == CredentialFormat.JWT_VC_JSON) {
                    JsonArray(listOf(url, alias).map {
                        JsonArray(listOf(JsonObject(w3cStatus(7) - "id" + ("statusListCredential" to JsonPrimitive(it)))))
                    })
                } else {
                    JsonArray(listOf(tokenStatus(7, url), tokenStatus(7, alias)))
                }

                val error = assertFailsWith<IllegalArgumentException>("$format: $alias") {
                    CredentialStatusConfiguration.parse(inventory, format)
                }
                assertContains(error.message.orEmpty(), "duplicate status references")
            }
        }
    }

    @Test
    fun statusListUrisRejectFragments() {
        for (uri in listOf("https://status.example.com/list#first", "https://status.example.com/list#")) {
            val tokenInventory = JsonArray(listOf(tokenStatus(7, uri)))
            val w3cEntry = JsonObject(w3cStatus(7) + ("statusListCredential" to JsonPrimitive(uri)))
            val w3cInventory = JsonArray(listOf(JsonArray(listOf(w3cEntry))))
            val configurations = listOf(
                Triple(CredentialFormat.SD_JWT_VC, tokenInventory, "status_list.uri"),
                Triple(CredentialFormat.MSO_MDOC, tokenInventory, "status_list.uri"),
                Triple(CredentialFormat.JWT_VC_JSON, w3cInventory, "statusListCredential"),
            )
            for ((format, inventory, field) in configurations) {
                val error = assertFailsWith<IllegalArgumentException>("$format: $uri") {
                    CredentialStatusConfiguration.parse(inventory, format)
                }
                assertEquals("$field must be a fragment-free URI", error.message)
            }
        }
    }

    @Test
    fun statusOwnershipNormalizationPreservesTheMdocUriValue() {
        val uri = "https://status.example.com/unused/../list"
        assertEquals(uri, CredentialStatusConfiguration.tokenStatusList(tokenStatus(7, uri)).uri.string)
    }

    @Test
    fun statusListComparisonPreservesNonDefaultPortsPathsAndQueries() {
        val urls = listOf(
            "https://status.example.com/List?a=1",
            "https://status.example.com:8443/List?a=1",
            "https://status.example.com/list?a=1",
            "https://status.example.com/List?a=2",
        )
        val configuration = assertIs<CredentialStatusConfiguration.Preallocated>(
            CredentialStatusConfiguration.parse(JsonArray(urls.map { tokenStatus(7, it) }), CredentialFormat.MSO_MDOC),
        )
        assertEquals(urls.size, configuration.groups.flatMap { it.references }.distinct().size)
    }

    @Test
    fun statusListComparisonPreservesReservedEscapesAndEmptyPathSegments() {
        val urls = listOf(
            "https://status.example.com/list/item",
            "https://status.example.com/list%2Fitem",
            "https://status.example.com/list//item",
        )
        val inventory = JsonArray(urls.map { tokenStatus(7, it) })
        val configuration = assertIs<CredentialStatusConfiguration.Preallocated>(
            CredentialStatusConfiguration.parse(inventory, CredentialFormat.SD_JWT_VC),
        )
        assertEquals(urls.size, configuration.groups.flatMap { it.references }.distinct().size)
    }

    @Test
    fun sdJwtStatusListComparisonPreservesQueryParameterOrder() {
        val url = "https://status.example.com/list"
        val first = tokenStatus(7, "$url?a=1&b=2&a=3")
        val second = tokenStatus(7, "$url?a=1&a=3&b=2")
        val configuration = assertIs<CredentialStatusConfiguration.Preallocated>(
            CredentialStatusConfiguration.parse(JsonArray(listOf(first, second)), CredentialFormat.SD_JWT_VC),
        )
        assertEquals(2, configuration.groups.flatMap { it.references }.distinct().size)
    }

    @Test
    fun mdocOwnershipUsesTheQueryAsEncodedInTheCredential() {
        val configured = tokenStatus(7, "https://status.example.com/list?a=1&b=2&a=3")
        val emittedUri = CredentialStatusConfiguration.tokenStatusList(configured).uri.string
        fun references(status: JsonObject, format: CredentialFormat) = assertIs<CredentialStatusConfiguration.Preallocated>(
            CredentialStatusConfiguration.parse(JsonArray(listOf(status)), format),
        ).groups.single().references

        assertEquals(
            references(tokenStatus(7, emittedUri), CredentialFormat.SD_JWT_VC),
            references(configured, CredentialFormat.MSO_MDOC),
        )
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
        for (key in listOf("status", "jwt:status")) {
            assertFailsWith<IllegalArgumentException> {
                CredentialStatusConfiguration.parse(tokenGroups(1), CredentialFormat.SD_JWT_VC,
                    buildJsonObject { put(key, "overwrite") })
            }
        }
        CredentialStatusConfiguration.parse(tokenGroups(1), CredentialFormat.SD_JWT_VC,
            buildJsonObject {
                put("status-label", "active")
                put("status.label", "active")
                putJsonObject("credentialSubject") { put("status", "active") }
            })
        val w3cGroups = JsonArray(listOf(JsonArray(listOf(w3cStatus(1)))))
        for (key in listOf("credentialStatus", "jwt:credentialStatus", "jwt:vc")) {
            assertFailsWith<IllegalArgumentException> {
                CredentialStatusConfiguration.parse(w3cGroups, CredentialFormat.JWT_VC_JSON,
                    buildJsonObject { put(key, "overwrite") })
            }
        }
        CredentialStatusConfiguration.parse(w3cGroups, CredentialFormat.JWT_VC_JSON,
            buildJsonObject {
                put("credentialStatus-label", "active")
                put("vc.credentialStatus", "active")
            })
    }

    companion object {
        fun tokenStatus(index: Int, uri: String = "https://status.example.com/1"): JsonObject = buildJsonObject {
            putJsonObject("status_list") { put("idx", index); put("uri", uri) }
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
