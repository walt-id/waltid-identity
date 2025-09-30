import id.walt.mdoc.namespaces.MdocSignedMerger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MdocMergingTests {

    val issuerSigned = Json.decodeFromString<JsonObject>(
        """
        {
           "iso.mdl": {
              "firstname": "Max",
              "lastname": "Muster",
              "age": 120
           },
           "iso.photoid": {
              "portrait": [1, 2, 3]
           },
           "untouched.namespace": { "k": "l" }
        }
    """.trimIndent()
    )

    val deviceSigned = Json.decodeFromString<JsonObject>(
        """
        {
           "iso.mdl": {
              "age": 121,
              "address": "Straße 1"
           },
           "iso.photoid": {
              "portrait": [3, 4, 5],
              "cust": 2
           },
           "namespace2": { "x": "y" }
        }
    """.trimIndent()
    )

    private val prettyJson = Json { prettyPrint = true }

    @Test
    fun testClash() {
        println("Should throw:")
        assertFailsWith<IllegalArgumentException> {
            try {
                val out = MdocSignedMerger.merge(issuerSigned, deviceSigned, strategy = MdocSignedMerger.MdocDuplicatesMergeStrategy.CLASH)
                println(prettyJson.encodeToString(out))
            } catch (e: Throwable) {
                println("OK: Thrown exception: ${e.stackTraceToString()}")
                throw e
            }
        }
    }

    @Test
    fun testOverride() {
        val out = MdocSignedMerger.merge(issuerSigned, deviceSigned, strategy = MdocSignedMerger.MdocDuplicatesMergeStrategy.OVERRIDE)
        println(prettyJson.encodeToString(out))
        // language="JSON"
        assertEquals(
            Json.decodeFromString<JsonObject>(
                """
        {
          "iso.mdl": {
            "firstname": "Max",
            "lastname": "Muster",
            "age": 121,
            "address": "Straße 1"
          },
          "iso.photoid": {
            "portrait": [
              3,
              4,
              5
            ],
            "cust": 2
          },
          "untouched.namespace": {
            "k": "l"
          },
          "namespace2": {
            "x": "y"
          }
        }
        """.trimIndent()
            ), out
        )
    }

    @Test
    fun testUseFirst() {
        val out = MdocSignedMerger.merge(issuerSigned, deviceSigned, strategy = MdocSignedMerger.MdocDuplicatesMergeStrategy.USE_FIRST)
        println(prettyJson.encodeToString(out))
        // language="JSON"
        assertEquals(
            Json.decodeFromString<JsonObject>(
                """
        {
          "iso.mdl": {
            "firstname": "Max",
            "lastname": "Muster",
            "age": 120,
            "address": "Straße 1"
          },
          "iso.photoid": {
            "portrait": [
              1,
              2,
              3
            ],
            "cust": 2
          },
          "untouched.namespace": {
            "k": "l"
          },
          "namespace2": {
            "x": "y"
          }
        } 
        """.trimIndent()
            ), out
        )
    }
}
