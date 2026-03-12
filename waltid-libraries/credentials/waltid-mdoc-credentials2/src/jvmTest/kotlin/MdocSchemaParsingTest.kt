import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.mdoc.schema.MdocsSchema
import id.walt.mdoc.schema.MdocsSchema.MdocsDatatype.*
import id.walt.mdoc.schema.MdocsSchema.MdocsSchemaType
import kotlin.test.Test
import kotlin.test.assertEquals

class MdocSchemaParsingTest {

    val doctype = "walt.id.credential"

    val ns1 = "walt.id.credential.ns1"
    val ns2 = "waltid.credential.nsspecial"

    @Test
    fun testMdocSchemaParsing() {
        val json = mapOf(
            doctype to mapOf(
                ns1 to mapOf(
                    "name" to "string", // tstr
                    "portrait" to "bytes", // bstr
                    "measurement" to "int",
                    "count" to "long",
                    "weight" to "uint", // uint
                    "flag" to "boolean", // bool
                    "birth_date" to "date", // full-date
                    "issue_date" to "date-time" // tdate
                ),
                ns2 to mapOf(
                    "nationalities" to "array>string",
                    "organization_location" to "map>string",
                    "driving_privileges" to "array>map>string"
                )
            )
        ).toJsonObject()

        val parsedSchema = MdocsSchema.parseFromJson(json)
        println(parsedSchema)

        val expected = MdocsSchema(
            mapOf(
                doctype to mapOf(
                    ns1 to mapOf(
                        "name" to MdocsSchemaType(STRING),
                        "portrait" to MdocsSchemaType(BYTES),
                        "measurement" to MdocsSchemaType(INT),
                        "count" to MdocsSchemaType(LONG),
                        "weight" to MdocsSchemaType(UINT),
                        "flag" to MdocsSchemaType(BOOLEAN),
                        "birth_date" to MdocsSchemaType(DATE),
                        "issue_date" to MdocsSchemaType(DATETIME)
                    ),
                    ns2 to mapOf(
                        "nationalities" to MdocsSchemaType(ARRAY, STRING),
                        "organization_location" to MdocsSchemaType(MAP, STRING),
                        "driving_privileges" to MdocsSchemaType(ARRAY, MdocsSchemaType(MAP, STRING))
                    )
                )
            )
        )

        assertEquals(expected = expected, actual = parsedSchema)
    }

}
