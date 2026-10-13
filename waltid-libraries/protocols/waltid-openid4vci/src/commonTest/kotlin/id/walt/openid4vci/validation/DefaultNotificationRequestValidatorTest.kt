package id.walt.openid4vci.validation

import id.walt.openid4vci.errors.NotificationErrorCodes
import id.walt.openid4vci.requests.notification.DefaultNotificationRequest
import id.walt.openid4vci.requests.notification.NotificationEvent
import id.walt.openid4vci.requests.notification.NotificationRequestValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DefaultNotificationRequestValidatorTest {
    private val validator = DefaultNotificationRequestValidator()
    private val parameters = mapOf(
        "notification_id" to listOf("notification"),
        "event" to listOf("credential_accepted"),
    )

    @Test
    fun acceptsEveryEventAndBuildsTheDefaultRequest() {
        for (event in NotificationEvent.entries) {
            val result = assertIs<NotificationRequestValidationResult.Success>(
                validator.validate(parameters + ("event" to listOf(event.wireValue))),
            )
            assertEquals(DefaultNotificationRequest("notification", event), result.request)
            assertNull(result.request.eventDescription)
        }
    }

    @Test
    fun rejectsMissingAndRepeatedRequiredParameters() {
        for ((name, values) in parameters) {
            assertInvalid(parameters - name)
            assertInvalid(parameters + (name to emptyList()))
            assertInvalid(parameters + (name to values + values))
        }
    }

    @Test
    fun rejectsBlankIdsAndUnsupportedEvents() {
        for (id in listOf("", " ", "\t\n")) assertInvalid(parameters + ("notification_id" to listOf(id)))
        for (event in listOf("", "accepted", "CREDENTIAL_ACCEPTED", " credential_accepted")) {
            assertInvalid(parameters + ("event" to listOf(event)))
        }
    }

    @Test
    fun acceptsEmptyDescriptionAndAllPermittedAsciiCharacters() {
        val permittedAscii = ((0x20..0x21) + (0x23..0x5B) + (0x5D..0x7E)).map(Int::toChar).joinToString("")
        for (description in listOf("", "Stored", permittedAscii)) {
            val result = assertIs<NotificationRequestValidationResult.Success>(
                validator.validate(parameters + ("event_description" to listOf(description))),
            )
            assertEquals(description, result.request.eventDescription)
        }
    }

    @Test
    fun rejectsInvalidDescriptionCharactersAndMultiplicity() {
        val invalidCharacters = (0..0x1F).map(Int::toChar) + listOf('"', '\\', '\u007F', '\u0080', 'é')
        for (character in invalidCharacters) {
            assertInvalid(parameters + ("event_description" to listOf("before${character}after")))
        }
        assertInvalid(parameters + ("event_description" to emptyList()))
        assertInvalid(parameters + ("event_description" to listOf("stored", "stored")))
    }

    @Test
    fun ignoresUnknownParametersRegardlessOfMultiplicity() {
        for (values in listOf(emptyList(), listOf("one"), listOf("one", "two"))) {
            val result = assertIs<NotificationRequestValidationResult.Success>(
                validator.validate(parameters + ("future_parameter" to values)),
            )
            assertEquals(DefaultNotificationRequest("notification", NotificationEvent.CREDENTIAL_ACCEPTED), result.request)
        }
    }

    private fun assertInvalid(parameters: Map<String, List<String>>) {
        val result = assertIs<NotificationRequestValidationResult.Failure>(validator.validate(parameters))
        assertEquals(NotificationErrorCodes.INVALID_NOTIFICATION_REQUEST, result.error.error)
    }
}
