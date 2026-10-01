package id.walt.openid4vci.json

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
}
