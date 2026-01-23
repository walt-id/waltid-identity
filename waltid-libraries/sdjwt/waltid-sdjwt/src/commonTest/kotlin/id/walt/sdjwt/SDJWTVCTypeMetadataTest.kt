package id.walt.sdjwt


import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft04
import id.walt.sdjwt.metadata.type.SdJwtVcTypeMetadataDraft13
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SDJWTVCTypeMetadataTest {

    @Test
    fun shouldDeserializeMetadataWithCustomParameters() {
        val metadataJsonString = """
            {
                "vct": "https://credentials.walt.id/identity_credential",
                "additional-parameter": "why not do it"
            }
        """.trimIndent()

        listOf(
            Json.decodeFromString<SdJwtVcTypeMetadataDraft04>(metadataJsonString),
            SdJwtVcTypeMetadataDraft04.fromJSONString(metadataJsonString),
        ).forEach {
            assertNotNull(it)
            val customParams = assertNotNull(it.customParameters)
            assertEquals("https://credentials.walt.id/identity_credential", it.vct)
            val additionalParam = assertNotNull(customParams["additional-parameter"])
            assertEquals("why not do it", additionalParam.jsonPrimitive.content)
        }
        listOf(
            Json.decodeFromString<SdJwtVcTypeMetadataDraft13>(metadataJsonString),
            SdJwtVcTypeMetadataDraft13.fromJSONString(metadataJsonString),
        ).forEach {
            assertNotNull(it)
            val customParams = assertNotNull(it.customParameters)
            assertEquals("https://credentials.walt.id/identity_credential", it.vct)
            val additionalParam = assertNotNull(customParams["additional-parameter"])
            assertEquals("why not do it", additionalParam.jsonPrimitive.content)
        }
    }

    @Test
    fun shouldSerializeMetadataWithCustomParameters() {
        val metadataJsonObject = buildJsonObject {
            put("vct", JsonPrimitive("https://credentials.walt.id/identity_credential"))
            put("hello-p", JsonPrimitive("world"))
        }
        listOf(
            SdJwtVcTypeMetadataDraft04.fromJSON(metadataJsonObject).toJSONString(),
            SdJwtVcTypeMetadataDraft13.fromJSON(metadataJsonObject).toJSONString(),
        ).forEach {
            assertNotNull(it)
            //there must not be a property named "customParameters" in the json
            assertNull("customParameters".toRegex().find(it))
            //instead we expect a property "hello-p" in the json
            assertNotNull("hello-p\"\\s*:\\s*\"world\"".toRegex().find(it))
        }
    }

    @Test
    fun `should be able to parse draft 04 type metadata`() {
        //https://datatracker.ietf.org/doc/html/draft-ietf-oauth-sd-jwt-vc-04#name-type-metadata-example
        val draft04TypeMetadataJsonPayload = """
            {
                "vct": "https://betelgeuse.example.com/education_credential",
                "name": "Betelgeuse Education Credential - Preliminary Version",
                "description": "This is our development version of the education credential. Don't panic.",
                "extends": "https://galaxy.example.com/galactic-education-credential-0.9",
                "extends#integrity": "sha256-9cLlJNXN-TsMk-PmKjZ5t0WRL5ca_xGgX3c1VLmXfh-WRL5",
                "schema_uri": "https://exampleuniversity.com/public/credential-schema-0.9",
                "schema_uri#integrity": "sha256-o984vn819a48ui1llkwPmKjZ5t0WRL5ca_xGgX3c1VLmXfh"
            }
        """.trimIndent()

        val parsed = Json.decodeFromString<SdJwtVcTypeMetadataDraft04>(
            draft04TypeMetadataJsonPayload
        )

        assertEquals(
            expected = "https://betelgeuse.example.com/education_credential",
            actual = parsed.vct,
        )

        assertEquals(
            expected = "Betelgeuse Education Credential - Preliminary Version",
            actual = parsed.name,
        )

        assertEquals(
            expected = "This is our development version of the education credential. Don't panic.",
            actual = parsed.description,
        )


        assertEquals(
            expected = "https://galaxy.example.com/galactic-education-credential-0.9",
            actual = parsed.extends,
        )


        assertEquals(
            expected = "sha256-9cLlJNXN-TsMk-PmKjZ5t0WRL5ca_xGgX3c1VLmXfh-WRL5",
            actual = parsed.extendsIntegrity,
        )


        assertEquals(
            expected = "https://exampleuniversity.com/public/credential-schema-0.9",
            actual = parsed.schemaUri,
        )


        assertEquals(
            expected = "sha256-o984vn819a48ui1llkwPmKjZ5t0WRL5ca_xGgX3c1VLmXfh",
            actual = parsed.schemaUriIntegrity,
        )

        assertEquals(
            expected = emptyMap(),
            actual = parsed.customParameters,
        )

    }

    @Test
    fun `should be able to parse draft 13 type metadata`() {
        //https://datatracker.ietf.org/doc/html/draft-ietf-oauth-sd-jwt-vc-13#name-example-2-type-metadata
        val draft13TypeMetadataJsonPayload = """
            {
                "vct": "https://betelgeuse.example.com/education_credential",
                "name": "Betelgeuse Education Credential - Preliminary Version",
                "description": "This is our development version of the education credential. Don't panic.",
                "extends": "https://galaxy.example.com/galactic-education-credential-0.9",
                "extends#integrity": "sha256-ilOUJsTultOwLfz7QUcFALaRa3BP/jelX1ds04kB9yU=",
                "display": [
                    {
                        "locale": "en-US",
                        "name": "Betelgeuse Education Credential",
                        "description": "An education credential for all carbon-based life forms on Betelgeusians",
                        "rendering": {
                            "simple": {
                                "logo": {
                                    "uri": "https://betelgeuse.example.com/public/education-logo.png",
                                    "uri#integrity": "sha256-LmXfh+9cLlJNXN+TsMk+PmKjZ5t0WRL5ca/xGgX3c1U=",
                                    "alt_text": "Betelgeuse Ministry of Education logo"
                                },
                                "background_image": {
                                    "uri": "https://betelgeuse.example.com/public/credential-background.png",
                                    "uri#integrity": "sha256-5sBT7mMLylHLWrrS/qQ8aHpRAxoraWVmWX6eUVMlrrA="
                                },
                                "background_color": "#12107c",
                                "text_color": "#FFFFFF"
                            },
                            "svg_templates": [
                                {
                                    "uri": "https://betelgeuse.example.com/public/credential-english.svg",
                                    "uri#integrity": "sha256-I4JcBGO7UfrkOBrsV7ytNJAfGuKLQh+e+Z31mc7iAb4=",
                                    "properties": {
                                        "orientation": "landscape",
                                        "color_scheme": "light",
                                        "contrast": "high"
                                    }
                                }
                            ]
                        }
                    },
                    {
                        "locale": "de-DE",
                        "name": "Betelgeuse-Bildungsnachweis",
                        "rendering": {
                            "simple": {
                                "logo": {
                                    "uri": "https://betelgeuse.example.com/public/education-logo-de.png",
                                    "uri#integrity": "sha256-LmXfh+9cLlJNXN+TsMk+PmKjZ5t0WRL5ca/xGgX3c1U=",
                                    "alt_text": "Logo des Betelgeusischen Bildungsministeriums"
                                },
                                "background_image": {
                                    "uri": "https://betelgeuse.example.com/public/credential-background-de.png",
                                    "uri#integrity": "sha256-9cLlJNXN+TsMk+PmKjZ5t0WRL5ca/xGgX3c1ULmXfh="
                                },
                                "background_color": "#12107c",
                                "text_color": "#FFFFFF"
                            },
                            "svg_templates": [
                                {
                                    "uri": "https://betelgeuse.example.com/public/credential-german.svg",
                                    "uri#integrity": "sha256-I4JcBGO7UfrkOBrsV7ytNJAfGuKLQh+e+Z31mc7iAb4=",
                                    "properties": {
                                        "orientation": "landscape",
                                        "color_scheme": "light",
                                        "contrast": "high"
                                    }
                                }
                            ]
                        }
                    }
                ],
                "claims": [
                    {
                        "path": [
                            "name"
                        ],
                        "display": [
                            {
                                "locale": "de-DE",
                                "label": "Vor- und Nachname",
                                "description": "Der Name des Studenten"
                            },
                            {
                                "locale": "en-US",
                                "label": "Name",
                                "description": "The name of the student"
                            }
                        ],
                        "sd": "always",
                        "mandatory": true
                    },
                    {
                        "path": [
                            "address"
                        ],
                        "display": [
                            {
                                "locale": "de-DE",
                                "label": "Adresse",
                                "description": "Adresse zum Zeitpunkt des Abschlusses"
                            },
                            {
                                "locale": "en-US",
                                "label": "Address",
                                "description": "Address at the time of graduation"
                            }
                        ],
                        "sd": "always"
                    },
                    {
                        "path": [
                            "address",
                            "street_address"
                        ],
                        "display": [
                            {
                                "locale": "de-DE",
                                "label": "Straße"
                            },
                            {
                                "locale": "en-US",
                                "label": "Street Address"
                            }
                        ],
                        "sd": "always",
                        "svg_id": "address_street_address"
                    },
                    {
                        "path": [
                            "degrees"
                        ],
                        "display": [
                            {
                                "locale": "de-DE",
                                "label": "Abschlüsse",
                                "description": "Abschlüsse des Studenten"
                            },
                            {
                                "locale": "en-US",
                                "label": "Degrees",
                                "description": "Degrees earned by the student"
                            }
                        ],
                        "sd": "never"
                    },
                    {
                        "path": [
                            "degrees",
                            null
                        ],
                        "sd": "always"
                    },
                    {
                        "path": [
                            "degrees",
                            null,
                            "field_of_study"
                        ],
                        "display": [
                            {
                                "locale": "de-DE",
                                "label": "Studienfach"
                            },
                            {
                                "locale": "en-US",
                                "label": "Field of Study"
                            }
                        ],
                        "sd": "never"
                    },
                    {
                        "path": [
                            "degrees",
                            null,
                            "date_awarded"
                        ],
                        "display": [
                            {
                                "locale": "de-DE",
                                "label": "Verleihungsdatum"
                            },
                            {
                                "locale": "en-US",
                                "label": "Date Awarded"
                            }
                        ],
                        "sd": "always"
                    }
                ]
            }
        """.trimIndent()

        val parsed = Json.decodeFromString<SdJwtVcTypeMetadataDraft13>(
            draft13TypeMetadataJsonPayload
        )

        assertEquals(
            expected = "https://betelgeuse.example.com/education_credential",
            actual = parsed.vct,
        )

        assertEquals(
            expected = "Betelgeuse Education Credential - Preliminary Version",
            actual = parsed.name,
        )

        assertEquals(
            expected = "This is our development version of the education credential. Don't panic.",
            actual = parsed.description,
        )

        assertEquals(
            expected = "https://galaxy.example.com/galactic-education-credential-0.9",
            actual = parsed.extends,
        )

        assertEquals(
            expected = "sha256-ilOUJsTultOwLfz7QUcFALaRa3BP/jelX1ds04kB9yU=",
            actual = parsed.extendsIntegrity,
        )


    }
}
