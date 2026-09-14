@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.commons.web.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.NotFoundException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestBodyErrorMessagesTest {

    @Test
    fun `humanizes missing required fields`() {
        val missing = MissingFieldException(listOf("profile", "preferences"), "AccountRegistrationRequest")
        val cause = BadRequestException(
            "Failed to convert request body to class id.walt.enterprise.AccountRegistrationRequest",
            JsonConvertException("Illegal input: Fields [profile, preferences] are required", missing),
        )

        val info = humanizeRequestBodyError(cause)
        assertNotNull(info)
        assertEquals("Request body is missing required field(s): profile, preferences", info.message)
        assertEquals(
            listOf("profile", "preferences"),
            info.details["missingFields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("AccountRegistrationRequest", info.details["expectedType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `humanizes unknown keys`() {
        val decoding = SerializationException(
            "Encountered unknown key 'foo'.\nUse 'ignoreUnknownKeys = true' in 'Json {}' builder to ignore unknown keys.\nJSON input: {\"foo\":1}",
        )
        val cause = BadRequestException(
            "Failed to convert request body to class id.walt.example.SomeRequest",
            JsonConvertException("Illegal input: Encountered unknown key 'foo'", decoding),
        )

        val info = humanizeRequestBodyError(cause)
        assertNotNull(info)
        assertEquals("Request body contains unknown field(s): foo", info.message)
        assertEquals(listOf("foo"), info.details["unknownFields"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("SomeRequest", info.details["expectedType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `humanizes missing class discriminator for TypedKeyGenerationRequest`() {
        val decoding = SerializationException(
            "Class discriminator was missing and no default serializers were registered in the polymorphic scope of 'TypedKeyGenerationRequest'.\nJSON input: {\"keyType\":\"Ed25519\"}",
        )
        val cause = BadRequestException(
            "Failed to convert request body to class id.walt.crypto.keys.TypedKeyGenerationRequest",
            JsonConvertException("Illegal input: Class discriminator was missing", decoding),
        )

        val info = humanizeRequestBodyError(cause)
        assertNotNull(info)
        assertEquals(
            "Request body is missing required discriminator field 'backend' for TypedKeyGenerationRequest",
            info.message,
        )
        assertEquals("TypedKeyGenerationRequest", info.details["expectedType"]!!.jsonPrimitive.content)
        assertEquals("backend", info.details["discriminator"]!!.jsonPrimitive.content)
    }

    @Test
    fun `humanizes enterprise convertingReceive wrapper`() {
        val missing = MissingFieldException(listOf("name"), "CreateOrgRequest")
        val badRequest = BadRequestException(
            "Failed to convert request body to class id.walt.enterprise.CreateOrgRequest",
            JsonConvertException("Illegal input: Field 'name' is required", missing),
        )
        val wrapped = IllegalArgumentException(
            "Unable to convert the supplied request body to the expected type: CreateOrgRequest, due to: ${badRequest.message}",
            badRequest,
        )

        val info = humanizeRequestBodyError(wrapped)
        assertNotNull(info)
        assertEquals("Request body is missing required field(s): name", info.message)
        assertEquals("CreateOrgRequest", info.details["expectedType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ignores unrelated IllegalArgumentException`() {
        assertNull(humanizeRequestBodyError(IllegalArgumentException("wallet id is required")))
    }

    @Test
    fun `exceptionMap uses humanized message and details`() {
        val missing = MissingFieldException(listOf("profile"), "AccountRegistrationRequest")
        val cause = BadRequestException(
            "Failed to convert request body to class id.walt.enterprise.AccountRegistrationRequest",
            JsonConvertException("Illegal input: Field 'profile' is required", missing),
        )

        val json = exceptionMap(cause, HttpStatusCode.BadRequest)
        assertEquals(true, json["exception"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("BadRequestException", json["id"]!!.jsonPrimitive.content)
        assertEquals("Request body is missing required field(s): profile", json["message"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("profile"),
            json["details"]!!.jsonObject["missingFields"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertTrue(json.keys.none { it.startsWith("cause") })
    }

    @Test
    fun `exceptionMap leaves unrelated errors unchanged`() {
        val cause = IllegalArgumentException("wallet id is required")
        val json = exceptionMap(cause, HttpStatusCode.BadRequest)
        assertEquals("wallet id is required", json["message"]!!.jsonPrimitive.content)
        assertNull(json["details"])
    }

    @Test
    fun `a body that cannot be deserialized is the caller's fault, not a server error`() {
        // CannotTransformContentToTypeException extends IOException rather than BadRequestException, so it
        // used to fall through to the catch-all and report 500 for a malformed request body.
        assertEquals(
            HttpStatusCode.BadRequest,
            statusCodeForException(CannotTransformContentToTypeException(typeOf<String>())),
        )
    }

    @Test
    fun `server-side failures still report as server errors`() {
        assertEquals(HttpStatusCode.InternalServerError, statusCodeForException(IllegalStateException("broken")))
        assertEquals(HttpStatusCode.InternalServerError, statusCodeForException(RuntimeException("broken")))
        assertEquals(HttpStatusCode.NotFound, statusCodeForException(NotFoundException("absent")))
        assertEquals(HttpStatusCode.BadRequest, statusCodeForException(BadRequestException("bad")))
    }
}
