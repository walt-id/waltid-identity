package id.walt.openid4vci.json

import id.walt.openid4vci.requests.notification.notificationRequestParameters
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuplicateJsonMembersTest {
    @Test
    fun detectsDuplicateMembersAndEscapedKeys() {
        assertTrue(jsonHasDuplicateMembers("""{"event":"credential_accepted","event":"credential_failure"}"""))
        assertTrue(jsonHasDuplicateMembers("""{"nested":{"a":1,"a":2}}"""))
        assertTrue(jsonHasDuplicateMembers("""[{"k":1,"k":2}]"""))
        assertTrue(jsonHasDuplicateMembers("""{"a\nb":1,"a\nb":2}"""))
        assertTrue(jsonHasDuplicateMembers("""{"\u0065vent":1,"event":2}"""))
        assertFalse(jsonHasDuplicateMembers("""{"notification_id":"id","event":"credential_accepted"}"""))
        assertFalse(jsonHasDuplicateMembers("""{"outer":{"a":1},"a":2}"""))
        assertFalse(jsonHasDuplicateMembers("{"))
        assertFalse(jsonHasDuplicateMembers(""))
    }
    @Test
    fun comparesDecodedKeysWithoutConflatingEscapesWithLiteralLetters() {
        for ((escape, unicode) in listOf("b" to "0008", "f" to "000c", "n" to "000a", "r" to "000d", "t" to "0009")) {
            assertTrue(jsonHasDuplicateMembers("""{"a\${escape}b":1,"a\u${unicode}b":2}"""))
            assertFalse(jsonHasDuplicateMembers("""{"a\${escape}b":1,"a${escape}b":2}"""))
        }
        assertTrue(jsonHasDuplicateMembers("""{"a\\b":1,"a\u005cb":2}"""))
        assertTrue(jsonHasDuplicateMembers("""{"a\"b":1,"a\u0022b":2}"""))
        assertTrue(jsonHasDuplicateMembers("""{"a\/b":1,"a/b":2}"""))
    }

    @Test
    fun notificationRejectsExcessiveNestingBeforeParsingTheJsonTree() {
        val prefix = """{"notification_id":"id","event":"credential_accepted","ignored":"""
        val atLimit = prefix + "[".repeat(127) + "0" + "]".repeat(127) + "}"
        assertTrue(notificationRequestParameters(atLimit).isNotEmpty())
        for (depth in listOf(128, 20_000)) {
            val excessive = prefix + "[".repeat(depth) + "0" + "]".repeat(depth) + "}"
            assertTrue(notificationRequestParameters(excessive).isEmpty())
        }
    }

}
