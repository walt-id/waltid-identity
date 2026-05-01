package id.walt.issuer.issuance

import io.ktor.server.plugins.BadRequestException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuanceCredentialStatusTest {

    @Test
    fun emptyObjectReturnsNull() {
        assertNull(buildJsonObject { }.toMdocIssuerStatusOrNull())
    }

    @Test
    fun statusListAndIdentifierListTogetherRejected() {
        val ex = assertFailsWith<BadRequestException> {
            buildJsonObject {
                put("status_list", buildJsonObject { put("idx", JsonPrimitive(0)); put("uri", JsonPrimitive("https://a.example/s")) })
                put("identifier_list", buildJsonObject { put("id", JsonPrimitive("00")); put("uri", JsonPrimitive("https://b.example/i")) })
            }.toMdocIssuerStatusOrNull()
        }
        assertTrue(ex.message!!.contains("9.1.2.6"), ex.message)
        assertTrue(ex.message!!.contains("identifier_list"), ex.message)
    }

    @Test
    fun statusListBlankUriRejected() {
        assertFailsWith<BadRequestException> {
            buildJsonObject {
                put(
                    "status_list",
                    buildJsonObject {
                        put("idx", JsonPrimitive(1))
                        put("uri", JsonPrimitive("   "))
                    },
                )
            }.toMdocIssuerStatusOrNull()
        }
    }

    @Test
    fun statusListRelativeUriRejected() {
        assertFailsWith<BadRequestException> {
            buildJsonObject {
                put(
                    "status_list",
                    buildJsonObject {
                        put("idx", JsonPrimitive(1))
                        put("uri", JsonPrimitive("/relative/path"))
                    },
                )
            }.toMdocIssuerStatusOrNull()
        }
    }

    @Test
    fun zeroXPrefixInvalidHexNoBase64Fallback() {
        assertFailsWith<BadRequestException> {
            buildJsonObject {
                put(
                    "identifier_list",
                    buildJsonObject {
                        put("id", JsonPrimitive("0xZZ"))
                        put("uri", JsonPrimitive("https://example.com/l"))
                    },
                )
            }.toMdocIssuerStatusOrNull()
        }
    }

    @Test
    fun statusListParsesNumericIdxAndHttpsUri() {
        val s = buildJsonObject {
            put(
                "status_list",
                buildJsonObject {
                    put("idx", JsonPrimitive(142))
                    put("uri", JsonPrimitive("https://status.example/lists/1"))
                },
            )
        }.toMdocIssuerStatusOrNull()
        assertEquals(142u, s!!.statusList!!.index)
        assertEquals("https://status.example/lists/1", s.statusList!!.uri)
    }

    @Test
    fun identifierListParsesHexIdWithoutPrefix() {
        val s = buildJsonObject {
            put(
                "identifier_list",
                buildJsonObject {
                    put("id", JsonPrimitive("abcd"))
                    put("uri", JsonPrimitive("https://id.example/lists/2"))
                },
            )
        }.toMdocIssuerStatusOrNull()
        assertEquals(listOf(0xab.toByte(), 0xcd.toByte()), s!!.identifierList!!.id.toList())
        assertEquals("https://id.example/lists/2", s.identifierList!!.uri)
    }
}
