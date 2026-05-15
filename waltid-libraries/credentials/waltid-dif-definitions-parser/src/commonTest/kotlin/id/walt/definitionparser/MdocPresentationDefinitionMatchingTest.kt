package id.walt.definitionparser

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class MdocPresentationDefinitionMatchingTest {

    @Test
    fun flatMdocNamespaceJsonMatchesPresentationDefinitionPaths() = runTest {
        val cred = Json.decodeFromString<JsonObject>(
            """
            {
              "id": "cred-1",
              "docType": "org.iso.18013.5.1.mDL",
              "org.iso.18013.5.1": {
                "family_name": "Doe"
              }
            }
            """.trimIndent()
        )

        val pd = Json.decodeFromString<PresentationDefinition>(
            """
            {
              "id": "pd-1",
              "input_descriptors": [
                {
                  "id": "mDL",
                  "format": { "mso_mdoc": { "alg": ["ES256"] } },
                  "constraints": {
                    "fields": [
                      {
                        "path": [ "${'$'}['docType']" ],
                        "filter": { "type": "string", "pattern": "^org.iso.18013.5.1.mDL${'$'}" }
                      },
                      {
                        "path": [ "${'$'}['org.iso.18013.5.1']['family_name']" ],
                        "filter": { "type": "string", "pattern": "^Doe${'$'}" }
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val descriptor = pd.inputDescriptors.single()
        val matched = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
            flowOf(cred),
            descriptor,
        ).toList()

        assertEquals(listOf(cred), matched)
    }

    @Test
    fun dcPrefixedNamespacePathMatchesDuplicateDcKeys() = runTest {
        val cred = Json.decodeFromString<JsonObject>(
            """
            {
              "id": "cred-2",
              "docType": "org.iso.18013.5.1.mDL",
              "dc:org.iso.18013.5.1": {
                "family_name": "Roe"
              }
            }
            """.trimIndent()
        )

        val pd = Json.decodeFromString<PresentationDefinition>(
            """
            {
              "id": "pd-2",
              "input_descriptors": [
                {
                  "id": "mDL",
                  "constraints": {
                    "fields": [
                      {
                        "path": [ "${'$'}['dc:org.iso.18013.5.1']['family_name']" ],
                        "filter": { "type": "string", "pattern": "^Roe${'$'}" }
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val matched = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
            flowOf(cred),
            pd.inputDescriptors.single(),
        ).toList()

        assertEquals(listOf(cred), matched)
    }

    @Test
    fun nullOrEmptyFieldConstraintsPassAllDocuments() = runTest {
        val cred = Json.decodeFromString<JsonObject>("""{"id":"x","docType":"org.iso.18013.5.1.mDL"}""")
        val pdNoFields = Json.decodeFromString<PresentationDefinition>(
            """
            {
              "id": "pd-empty",
              "input_descriptors": [
                {
                  "id": "any",
                  "constraints": { "fields": [] }
                }
              ]
            }
            """.trimIndent()
        )
        val matchedEmpty = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
            flowOf(cred),
            pdNoFields.inputDescriptors.single(),
        ).toList()
        assertEquals(listOf(cred), matchedEmpty)

        val pdNullFields = Json.decodeFromString<PresentationDefinition>(
            """
            {
              "id": "pd-null",
              "input_descriptors": [
                {
                  "id": "any",
                  "constraints": { "limit_disclosure": "required" }
                }
              ]
            }
            """.trimIndent()
        )
        val matchedNull = PresentationDefinitionParser.matchCredentialsForInputDescriptor(
            flowOf(cred),
            pdNullFields.inputDescriptors.single(),
        ).toList()
        assertEquals(listOf(cred), matchedNull)
    }
}
