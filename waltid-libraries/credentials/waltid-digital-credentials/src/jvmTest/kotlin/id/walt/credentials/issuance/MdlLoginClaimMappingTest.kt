package id.walt.credentials.issuance

import id.walt.mdoc.issuance.MdlAge
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MdlLoginClaimMappingTest {
    @Test
    fun preparesDirectBirthDateWhenNamespaceIsAbsentOrNull() {
        val mapping = MdlLoginClaimMapping.forDestinations(listOf("""$["${MdlAge.NAMESPACE}"]["birth_date"]"""))
        assertTrue(mapping.canSupplyBirthDate)

        for (data in missingNamespaces) {
            val destination = mapping.prepareDestination(data)
            assertEquals(JsonPrimitive(""), destination.getValue(MdlAge.NAMESPACE).jsonObject[MdlAge.BIRTH_DATE])
        }
    }

    @Test
    fun preparesDirectBirthDateWithinExistingNamespace() {
        val mapping = MdlLoginClaimMapping.forDestinations(listOf("$['${MdlAge.NAMESPACE}'].birth_date"))
        val givenName = JsonPrimitive("Jane")
        val originalNames = mapOf("given_name" to givenName)

        for (fields in listOf(originalNames, originalNames + (MdlAge.BIRTH_DATE to JsonNull))) {
            val data = JsonObject(mapOf(MdlAge.NAMESPACE to JsonObject(fields)))
            val destination = mapping.prepareDestination(data).getValue(MdlAge.NAMESPACE).jsonObject
            assertEquals(givenName, destination["given_name"])
            assertEquals(JsonPrimitive(""), destination[MdlAge.BIRTH_DATE])
            assertEquals(JsonObject(fields), data[MdlAge.NAMESPACE])
        }
    }

    @Test
    fun preparesWholeNamespaceWhenItIsAbsentOrNull() {
        val mapping = MdlLoginClaimMapping.forDestinations(listOf("$.['${MdlAge.NAMESPACE}']"))
        assertTrue(mapping.canSupplyBirthDate)

        for (data in missingNamespaces) {
            val destination = mapping.prepareDestination(data)
            assertEquals(JsonObject(emptyMap()), destination[MdlAge.NAMESPACE])
            assertEquals(data["other"], destination["other"])
        }
    }

    @Test
    fun preservesExistingDestinations() {
        val direct = MdlLoginClaimMapping.forDestinations(listOf("$['${MdlAge.NAMESPACE}']['birth_date']"))
        val whole = MdlLoginClaimMapping.forDestinations(listOf("$['${MdlAge.NAMESPACE}']"))
        val existing = JsonObject(mapOf(MdlAge.NAMESPACE to buildJsonObject { put("birth_date", "1990-08-26") }))
        val scalar = JsonObject(mapOf(MdlAge.NAMESPACE to JsonPrimitive("placeholder")))
        assertSame(existing, direct.prepareDestination(existing))
        assertSame(existing, whole.prepareDestination(existing))
        assertSame(scalar, direct.prepareDestination(scalar))
        assertSame(scalar, whole.prepareDestination(scalar))
    }

    @Test
    fun ignoresDestinationsForOtherClaims() {
        val mapping = MdlLoginClaimMapping.forDestinations(listOf("$['${MdlAge.NAMESPACE}'].given_name", "$..birth_date", "$["))
        assertFalse(mapping.canSupplyBirthDate)
        val data = missingNamespaces.first()
        assertSame(data, mapping.prepareDestination(data))
    }

    private val missingNamespaces = listOf(
        buildJsonObject { put("other", "kept") },
        buildJsonObject {
            put("other", "kept")
            put(MdlAge.NAMESPACE, JsonNull)
        },
    )
}
