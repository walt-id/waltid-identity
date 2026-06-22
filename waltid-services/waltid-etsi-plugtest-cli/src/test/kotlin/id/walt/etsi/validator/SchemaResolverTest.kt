package id.walt.etsi.validator

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [SchemaResolver]: vct -> Type Metadata `schema_url` -> local schema file.
 * Uses temp dirs mirroring the ETSI plugtest META-DATA/ and SCHEMA/ layout.
 */
class SchemaResolverTest {

    private fun tempDirs(): Pair<File, File> {
        val base = File.createTempFile("schematest", "").let { it.delete(); it.mkdirs(); it }
        val meta = File(base, "META-DATA").apply { mkdirs() }
        val schema = File(base, "SCHEMA").apply { mkdirs() }
        File(meta, "PID-Type-Metadata.json").writeText(
            """{ "vct": "urn:eudi:pid:1", "schema_url": "https://example.org/.well-known/schemas/urn-eudi-pid-1.schema.json" }"""
        )
        File(schema, "urn-eudi-pid-1.schema.json").writeText(
            """{ "${'$'}schema":"https://json-schema.org/draft/2020-12/schema", "type":"object", "required":["vct"] }"""
        )
        return schema to meta
    }

    @Test
    fun resolvesSchemaForKnownVct() {
        val (schema, meta) = tempDirs()
        val resolver = SchemaResolver(schemaDir = schema, metadataDir = meta)
        val resolved = resolver.resolveSchemaForVct("urn:eudi:pid:1")
        assertNotNull(resolved, "Schema must resolve for a vct present in Type Metadata + SCHEMA dir")
        assertEquals("object", resolved["type"]?.let { (it as kotlinx.serialization.json.JsonPrimitive).content })
    }

    @Test
    fun returnsNullForUnknownVct() {
        val (schema, meta) = tempDirs()
        val resolver = SchemaResolver(schemaDir = schema, metadataDir = meta)
        // Our own credentials use this vct, which has no published Type Metadata/schema -> skip.
        assertNull(resolver.resolveSchemaForVct("urn:etsi:eaa:credential"))
        assertNull(resolver.resolveSchemaForVct(null))
        assertNull(resolver.resolveSchemaForVct(""))
    }

    @Test
    fun returnsNullWhenDirsMissing() {
        val resolver = SchemaResolver(schemaDir = null, metadataDir = null)
        assertNull(resolver.resolveSchemaForVct("urn:eudi:pid:1"))
    }

    @Test
    fun returnsNullWhenSchemaFileAbsent() {
        // Metadata references a schema_url whose file is not present in SCHEMA dir -> skip (no fail).
        val base = File.createTempFile("schematest2", "").let { it.delete(); it.mkdirs(); it }
        val meta = File(base, "META-DATA").apply { mkdirs() }
        val schema = File(base, "SCHEMA").apply { mkdirs() }
        File(meta, "x.json").writeText(
            """{ "vct": "urn:eudi:pid:1", "schema_url": "https://example.org/schemas/missing.schema.json" }"""
        )
        val resolver = SchemaResolver(schemaDir = schema, metadataDir = meta)
        assertNull(resolver.resolveSchemaForVct("urn:eudi:pid:1"))
    }
}
