package id.walt.oid4vc

import id.walt.oid4vc.util.ShortIdUtils
import kotlin.test.Test
import kotlin.test.assertNotEquals

class SessionIdUtilTest {

    @Test
    fun nonEqualShortId() {
        val id1 = ShortIdUtils.randomSessionId()
        val id2 = ShortIdUtils.randomSessionId()

        assertNotEquals(id1, id2)
    }

}
