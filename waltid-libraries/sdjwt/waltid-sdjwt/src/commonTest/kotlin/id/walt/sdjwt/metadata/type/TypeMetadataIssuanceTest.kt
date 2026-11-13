package id.walt.sdjwt.metadata.type

import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class TypeMetadataIssuanceTest {
    private val vct = "urn:example:identity"

    private suspend fun metadata(claims: String, schema: JsonElement? = null): ResolvedTypeMetadata {
        val document = buildJsonObject {
            put("vct", vct); put("claims", Json.parseToJsonElement(claims))
            schema?.let { put("schema", it) }
        }.toString().encodeToByteArray()
        return TypeMetadataResolver({ document }).resolve(vct)
    }

    private fun payload(data: String, vararg disclosed: String) = SDPayload.createSDPayload(
        JsonObject(Json.parseToJsonElement(data).jsonObject + ("vct" to JsonPrimitive(vct))),
        SDMap.generateSDMap(disclosed.toList()),
    )

    @Test fun inheritedMandatoryAndDisclosureRulesApplyWithoutAnySchema() = runTest {
        val base = """{"vct":"urn:example:base","claims":[{"path":["family_name"],"mandatory":true,"sd":"always"}]}""".encodeToByteArray()
        val leaf = buildJsonObject {
            put("vct", vct); put("extends", "urn:example:base")
            put("extends#integrity", MetadataIntegrity.sha256(base))
        }.toString().encodeToByteArray()
        val resolved = TypeMetadataResolver({ if (it == vct) leaf else base }).resolve(vct)
        val noSchema: suspend (String) -> ByteArray = { error("No schema may be fetched") }
        assertFailsWith<TypeMetadataValidationException> { resolved.validateForIssuance(listOf(payload("{}")), noSchema) }
        assertFailsWith<TypeMetadataValidationException> { resolved.validateForIssuance(listOf(payload("""{"family_name":"Lovelace"}""")), noSchema) }
        resolved.validateForIssuance(listOf(payload("""{"family_name":"Lovelace"}""", "family_name")), noSchema)
    }

    @Test fun actualDirectDisclosureIsDistinctFromHidingAnAncestor() = runTest {
        val data = """{"address":{"city":"London"},"names":["Ada","Grace"]}"""
        val city = metadata("""[{"path":["address","city"],"sd":"always"}]""")
        assertFailsWith<TypeMetadataValidationException> {
            city.validateForIssuance(listOf(payload(data, "address"))) { error("No schema") }
        }
        city.validateForIssuance(listOf(payload(data, "address", "address.city"))) { error("No schema") }
        val never = metadata("""[{"path":["address","city"],"sd":"never"}]""")
        never.validateForIssuance(listOf(payload(data, "address"))) { error("No schema") }
        assertFailsWith<TypeMetadataValidationException> {
            never.validateForIssuance(listOf(payload(data, "address.city"))) { error("No schema") }
        }
        val array = metadata("""[{"path":["names",null],"sd":"always"}]""")
        array.validateForIssuance(listOf(payload(data, "names.[]"))) { error("No schema") }
        assertFailsWith<TypeMetadataValidationException> {
            array.validateForIssuance(listOf(payload(data, "names.[0]"))) { error("No schema") }
        }
    }

    @Test fun mandatoryWildcardsCheckEveryTraversedBranchAndAllowEmptyArrays() = runTest {
        val resolved = metadata("""[{"path":["members",null,"name"],"mandatory":true}]""")
        resolved.validateForIssuance(listOf(payload("""{"members":[]}"""))) { error("No schema") }
        resolved.validateForIssuance(listOf(payload("""{"members":[{"name":null},{"name":"Ada"}]}"""))) { error("No schema") }
        for (data in listOf("{}", """{"members":[{"name":"Ada"},{}]}""", """{"members":[1]}""")) {
            assertFailsWith<TypeMetadataValidationException> {
                resolved.validateForIssuance(listOf(payload(data))) { error("No schema") }
            }
        }
        val optional = metadata("""[{"path":["optional"],"sd":"always"}]""")
        optional.validateForIssuance(listOf(payload("{}"))) { error("No schema") }
    }

    @Test fun indexedAndWildcardRestrictionsBothApplyAndPathTypesStayDistinct() = runTest {
        val incompatible = metadata("""[
            {"path":["names",null],"sd":"always"},
            {"path":["names",0],"sd":"never"}
        ]""")
        for (paths in listOf(emptyArray(), arrayOf("names.[]"))) {
            assertFailsWith<TypeMetadataValidationException> {
                incompatible.validateForIssuance(listOf(payload("""{"names":["Ada"]}""", *paths))) { error("No schema") }
            }
        }
        val index = metadata("""[{"path":["names",1],"mandatory":true}]""")
        assertFailsWith<TypeMetadataValidationException> {
            index.validateForIssuance(listOf(payload("""{"names":["Ada"]}"""))) { error("No schema") }
        }
        val name = metadata("""[{"path":["names","0"],"mandatory":true}]""")
        name.validateForIssuance(listOf(payload("""{"names":{"0":"Ada"}}"""))) { error("No schema") }
        assertFailsWith<TypeMetadataValidationException> {
            name.validateForIssuance(listOf(payload("""{"names":["Ada"]}"""))) { error("No schema") }
        }
    }

    @Test fun schemasInspectFinalClaimsOncePerBatchAndRetainIndependentConstraints() = runTest {
        val root = """{"vct":"urn:example:base","schema_uri":"https://types.example/base.json"}""".encodeToByteArray()
        val leaf = buildJsonObject {
            put("vct", vct); put("extends", "urn:example:base"); put("schema_uri", "https://types.example/leaf.json")
            put("extends#integrity", MetadataIntegrity.sha256(root))
        }.toString().encodeToByteArray()
        val resolved = TypeMetadataResolver({ if (it == vct) leaf else root }).resolve(vct)
        val requests = mutableListOf<String>()
        val load: suspend (String) -> ByteArray = { uri ->
            requests += uri
            when (uri) {
                "https://types.example/base.json" -> """{"required":["iss"]}"""
                "https://types.example/leaf.json" -> """{"required":["family_name"]}"""
                else -> error("Unexpected schema URI")
            }.encodeToByteArray()
        }
        val good = payload("""{"iss":"https://issuer.example","family_name":"Lovelace"}""", "family_name")
        resolved.validateForIssuance(listOf(good, good), load)
        assertEquals(listOf("https://types.example/leaf.json", "https://types.example/base.json"), requests)
        assertFailsWith<TypeMetadataValidationException> {
            resolved.validateForIssuance(listOf(good, payload("""{"family_name":"Lovelace"}""")), load)
        }
        assertFailsWith<CancellationException> { resolved.validateForIssuance(listOf(good)) { throw CancellationException("cancelled") } }
    }

    @Test fun offerChecksDeferUnknownDataWithoutLosingDecidableDisclosureConflicts() = runTest {
        val resolved = metadata("""[{"path":["family_name"],"mandatory":true,"sd":"always"}]""")
        val disclosure = SDMap.generateSDMap(listOf("family_name"))
        assertFailsWith<TypeMetadataValidationException> { resolved.validateIssuanceInput(buildJsonObject {}, disclosure) }
        resolved.validateIssuanceInput(buildJsonObject {}, disclosure, setOf("family_name"))
        assertFailsWith<TypeMetadataValidationException> {
            resolved.validateIssuanceInput(buildJsonObject {}, null, setOf("family_name"))
        }
        val protected = metadata("""[{"path":["cnf","jwk"],"sd":"always"}]""")
        assertFailsWith<TypeMetadataValidationException> {
            protected.validateIssuanceInput(buildJsonObject {}, SDMap.generateSDMap(listOf("cnf.jwk")), setOf("cnf"))
        }
    }
}
