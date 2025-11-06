package id.walt.policies.policies.status

import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.model.StatusContent
import id.walt.policies.policies.status.model.W3CStatusContent
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StatusContentTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `test W3CStatusContent serialization with all fields`() {
        val content = W3CStatusContent(
            type = "BitstringStatusList",
            purpose = "revocation",
            list = "H4sIAAAAAAAAAGNgYGBgZGBkYGLg5mBg4GRgYuBjYGHgYeBh4GNgZeDl4GbgY-Bk4OXgZOBiEGDgYhBg4GIQYOBmEGDgZhBh4GYQYeBiEGPgYhBn4GIQYOBmEGDgZhBg4GYQ"
        )

        val jsonString = json.encodeToString<StatusContent>(content)
        val decoded = json.decodeFromString<StatusContent>(jsonString)

        assertEquals(content, decoded)
        assertTrue(decoded is W3CStatusContent)
        assertEquals("BitstringStatusList", decoded.type)
        assertEquals("revocation", decoded.purpose)
        assertEquals("H4sIAAAAAAAAAGNgYGBgZGBkYGLg5mBg4GRgYuBjYGHgYeBh4GNgZeDl4GbgY-Bk4OXgZOBiEGDgYhBg4GIQYOBmEGDgZhBh4GYQYeBiEGPgYhBn4GIQYOBmEGDgZhBg4GYQ", decoded.list)
    }

    @Test
    fun `test W3CStatusContent serialization with default values`() {
        val content = W3CStatusContent(
            type = "BitstringStatusList",
            list = "encodedData123"
        )

        val jsonString = json.encodeToString<StatusContent>(content)
        val decoded = json.decodeFromString<StatusContent>(jsonString)

        assertEquals(content, decoded)
        assertTrue(decoded is W3CStatusContent)
        assertEquals("revocation", decoded.purpose) // default value
    }

    @Test
    fun `test W3CStatusContent JSON field mapping`() {
        val jsonString = """
            {
                "type": "BitstringStatusList",
                "statusPurpose": "suspension",
                "statusSize": 4,
                "encodedList": "base64EncodedData"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<W3CStatusContent>(jsonString)

        assertEquals("BitstringStatusList", decoded.type)
        assertEquals("suspension", decoded.purpose)
        assertEquals("base64EncodedData", decoded.list)
    }

    @Test
    fun `test IETFStatusContent serialization`() {
        val content = IETFStatusContent(
            size = 16,
            list = "binaryStatusList"
        )

        val jsonString = json.encodeToString<StatusContent>(content)
        val decoded = json.decodeFromString<StatusContent>(jsonString)

        assertEquals(content, decoded)
        assertTrue(decoded is IETFStatusContent)
        assertEquals(16, decoded.size)
        assertEquals("binaryStatusList", decoded.list)
    }

    @Test
    fun `test IETFStatusContent with default values`() {
        val content = IETFStatusContent(list = "defaultSizeList")

        val jsonString = json.encodeToString<StatusContent>(content)
        val decoded = json.decodeFromString<StatusContent>(jsonString)

        assertEquals(content, decoded)
        assertTrue(decoded is IETFStatusContent)
        assertEquals(1, decoded.size) // default value
        assertEquals("defaultSizeList", decoded.list)
    }

    @Test
    fun `test IETFStatusContent JSON field mapping`() {
        val jsonString = """
            {
                "bits": 32,
                "lst": "compressedBitList"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<IETFStatusContent>(jsonString)

        assertEquals(32, decoded.size)
        assertEquals("compressedBitList", decoded.list)
    }

    @Test
    fun `test polymorphic serialization with status discriminator`() {
        val w3cContent: StatusContent = W3CStatusContent(
            type = "BitstringStatusList",
            purpose = "revocation",
            list = "w3cData"
        )

        val ietfContent: StatusContent = IETFStatusContent(
            size = 4,
            list = "ietfData"
        )

        val w3cJson = json.encodeToString<StatusContent>(w3cContent)
        val ietfJson = json.encodeToString<StatusContent>(ietfContent)

        // Verify discriminator field is present
        assertTrue(w3cJson.contains("\"status\":\"W3CStatusContent\""))
        assertTrue(ietfJson.contains("\"status\":\"IETFStatusContent\""))

        // Test deserialization
        val decodedW3c = json.decodeFromString<StatusContent>(w3cJson)
        val decodedIetf = json.decodeFromString<StatusContent>(ietfJson)

        assertEquals(w3cContent, decodedW3c)
        assertEquals(ietfContent, decodedIetf)
        assertTrue(decodedW3c is W3CStatusContent)
        assertTrue(decodedIetf is IETFStatusContent)
    }

    @Test
    fun `test polymorphic deserialization with explicit discriminator`() {
        val w3cJson = """
            {
                "status": "W3CStatusContent",
                "type": "BitstringStatusList",
                "statusPurpose": "revocation",
                "statusSize": 2,
                "encodedList": "w3cData"
            }
        """.trimIndent()

        val ietfJson = """
            {
                "status": "IETFStatusContent", 
                "bits": 4,
                "lst": "ietfData"
            }
        """.trimIndent()

        val decodedW3c = json.decodeFromString<StatusContent>(w3cJson)
        val decodedIetf = json.decodeFromString<StatusContent>(ietfJson)

        assertTrue(decodedW3c is W3CStatusContent)
        assertTrue(decodedIetf is IETFStatusContent)

        assertEquals("BitstringStatusList", decodedW3c.type)
        assertEquals("revocation", decodedW3c.purpose)
        assertEquals("w3cData", decodedW3c.list)

        assertEquals(4, decodedIetf.size)
        assertEquals("ietfData", decodedIetf.list)
    }

    @Test
    fun `test abstract properties are accessible`() {
        val w3cContent: StatusContent = W3CStatusContent(
            type = "test",
            list = "testList"
        )

        val ietfContent: StatusContent = IETFStatusContent(
            size = 10,
            list = "anotherList"
        )

        assertEquals("testList", w3cContent.list)
        assertEquals("anotherList", ietfContent.list)
    }

    @Test
    fun `test W3CStatusContent validation - required fields`() {
        // Test that required fields throw exceptions when missing
        val incompleteJson = """
            {
                "status": "W3CStatusContent",
                "statusPurpose": "revocation"
            }
        """.trimIndent()

        assertThrows<Exception> {
            json.decodeFromString<StatusContent>(incompleteJson)
        }
    }

    @Test
    fun `test IETFStatusContent validation - required fields`() {
        val incompleteJson = """
            {
                "status": "IETFStatusContent",
                "bits": 8
            }
        """.trimIndent()

        assertThrows<Exception> {
            json.decodeFromString<StatusContent>(incompleteJson)
        }
    }

    @Test
    fun `test edge cases - empty and null values`() {
        val w3cWithEmptyList = W3CStatusContent(
            type = "test",
            list = ""
        )

        val jsonString = json.encodeToString<StatusContent>(w3cWithEmptyList)
        val decoded = json.decodeFromString<StatusContent>(jsonString)

        assertEquals(w3cWithEmptyList, decoded)
        assertEquals("", (decoded as W3CStatusContent).list)
    }

    @Test
    fun `test null purpose in W3CStatusContent`() {
        val jsonWithNullPurpose = """
            {
                "type": "BitstringStatusList",
                "statusPurpose": null,
                "statusSize": 1,
                "encodedList": "testData"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<W3CStatusContent>(jsonWithNullPurpose)
        assertEquals(null, decoded.purpose)
    }

    @Test
    fun `test data class equality and hash code`() {
        val content1 = W3CStatusContent(
            type = "test",
            purpose = "revocation",
            list = "data123"
        )

        val content2 = W3CStatusContent(
            type = "test",
            purpose = "revocation",
            list = "data123"
        )

        val content3 = W3CStatusContent(
            type = "test",
            purpose = "suspension",
            list = "data123"
        )

        assertEquals(content1, content2)
        assertEquals(content1.hashCode(), content2.hashCode())
        assertTrue(content1 != content3)
    }

    @Test
    fun `test copy functionality`() {
        val original = W3CStatusContent(
            type = "original",
            purpose = "revocation",
            list = "originalData"
        )

        val modified = original.copy(purpose = "suspension")

        assertEquals("original", modified.type)
        assertEquals("suspension", modified.purpose)
        assertEquals("originalData", modified.list)
    }

    @Test
    fun `test toString implementation`() {
        val w3cContent = W3CStatusContent(
            type = "test",
            purpose = "revocation",
            list = "testData"
        )

        val ietfContent = IETFStatusContent(
            size = 4,
            list = "ietfTestData"
        )

        assertNotNull(w3cContent.toString())
        assertNotNull(ietfContent.toString())

        // Verify toString contains field values
        assertTrue(w3cContent.toString().contains("test"))
        assertTrue(w3cContent.toString().contains("revocation"))
        assertTrue(ietfContent.toString().contains("ietfTestData"))
    }
}
