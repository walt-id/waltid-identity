package id.walt.mdoc

import id.walt.mdoc.dataelement.*
import id.walt.mdoc.dataelement.json.JsonArrayToCborMappingConfig
import id.walt.mdoc.dataelement.json.JsonObjectToCborMappingConfig
import id.walt.mdoc.dataelement.json.JsonStringToCborMappingConfig
import id.walt.mdoc.dataelement.json.StringToCborTypeConversion
import id.walt.mdoc.doc.MDocNameSpaceBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MDocNameSpaceBuilderTest {

    companion object {

        private const val MDL_NAMESPACE_ID = "org.iso.18013.5.1"

        private val MDL_MANDATORY_FIELDS_DATA = Json.decodeFromString<JsonObject>(
            """
            {
                "family_name": "Doe",
                "given_name": "John",
                "birth_date": "1986-03-22T22:00:00Z",
                "issue_date": "2019-10-20",
                "expiry_date": "2024-10-20",
                "issuing_country": "AT",
                "issuing_authority": "AT DMV",
                "document_number": "123456789",
                "portrait": "AH8QIP8=",
                "driving_privileges": [
                    {
                        "vehicle_category_code": "A",
                        "issue_date": "2018-08-09",
                        "expiry_date": "2024-10-20T22:00:00Z"
                    },
                    {
                        "vehicle_category_code": "B",
                        "issue_date": "2017-02-23",
                        "expiry_date": "2024-10-20T22:00:00Z"
                    }
                ],
                "un_distinguishing_sign": "AT"
            }
        """.trimIndent()
        )

        private val MDL_NAMESPACE_MANDATORY_FIELDS_DATA_MAPPING_CONFIG = JsonObjectToCborMappingConfig(
            entriesConfigMap = mapOf(
                "birth_date" to JsonStringToCborMappingConfig(
                    conversionType = StringToCborTypeConversion.STRING_TO_T_DATE,
                ),
                "issue_date" to JsonStringToCborMappingConfig(
                    conversionType = StringToCborTypeConversion.STRING_TO_FULL_DATE,
                ),
                "expiry_date" to JsonStringToCborMappingConfig(
                    conversionType = StringToCborTypeConversion.STRING_TO_FULL_DATE,
                ),
                "portrait" to JsonStringToCborMappingConfig(
                    conversionType = StringToCborTypeConversion.BASE64_STRING_TO_BYTE_STRING,
                ),
                "driving_privileges" to JsonArrayToCborMappingConfig(
                    arrayConfig = listOf(
                        JsonObjectToCborMappingConfig(
                            entriesConfigMap = mapOf(
                                "issue_date" to JsonStringToCborMappingConfig(
                                    conversionType = StringToCborTypeConversion.STRING_TO_FULL_DATE,
                                ),
                                "expiry_date" to JsonStringToCborMappingConfig(
                                    conversionType = StringToCborTypeConversion.STRING_TO_T_DATE,
                                ),
                            ),
                        ),
                        JsonObjectToCborMappingConfig(
                            entriesConfigMap = mapOf(
                                "issue_date" to JsonStringToCborMappingConfig(
                                    conversionType = StringToCborTypeConversion.STRING_TO_FULL_DATE,
                                ),
                                "expiry_date" to JsonStringToCborMappingConfig(
                                    conversionType = StringToCborTypeConversion.STRING_TO_T_DATE,
                                ),
                            ),
                        ),
                    )
                ),
            )
        )
    }

    @Test
    fun testMdlNameSpaceBuilding() {
        val mDLNameSpace = MDocNameSpaceBuilder.fromJsonObjectMappingConfig(
            nameSpaceId = MDL_NAMESPACE_ID,
            jsonData = MDL_MANDATORY_FIELDS_DATA,
            dataMappingConfig = MDL_NAMESPACE_MANDATORY_FIELDS_DATA_MAPPING_CONFIG,
        )
        assertIs<StringElement>(mDLNameSpace.claimsMap.value[MapKey("family_name")])
        assertIs<StringElement>(mDLNameSpace.claimsMap.value[MapKey("given_name")])
        assertIs<StringElement>(mDLNameSpace.claimsMap.value[MapKey("issuing_country")])
        assertIs<StringElement>(mDLNameSpace.claimsMap.value[MapKey("issuing_authority")])
        assertIs<StringElement>(mDLNameSpace.claimsMap.value[MapKey("document_number")])
        
        assertIs<TDateElement>(mDLNameSpace.claimsMap.value[MapKey("birth_date")])
        assertIs<FullDateElement>(mDLNameSpace.claimsMap.value[MapKey("issue_date")])
        assertIs<FullDateElement>(mDLNameSpace.claimsMap.value[MapKey("expiry_date")])
        assertIs<ByteStringElement>(mDLNameSpace.claimsMap.value[MapKey("portrait")])
        val drivingPrivileges = assertIs<ListElement>(mDLNameSpace.claimsMap.value[MapKey("driving_privileges")])
        assertEquals(
            expected = 2,
            actual = drivingPrivileges.value.size,
        )
        drivingPrivileges.value.forEach {
            val drivingPrivilege = assertIs<MapElement>(it)
            assertIs<StringElement>(drivingPrivilege.value[MapKey("vehicle_category_code")])
            assertIs<FullDateElement>(drivingPrivilege.value[MapKey("issue_date")])
            assertIs<TDateElement>(drivingPrivilege.value[MapKey("expiry_date")])
        }

        assertIs<StringElement>(mDLNameSpace.claimsMap.value[MapKey("un_distinguishing_sign")])

    }

}