package id.walt.commons.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class InMemoryPersistenceTest {

    @Test
    fun `per-entry TTL overrides default expiration`() {
        val timeSource = TestTimeSource()
        val persistence = InMemoryPersistence<String>("test", 5.minutes, timeSource)
        persistence["default"] = "default-value"
        persistence.set("extended", "extended-value", 30.minutes)
        persistence.listAdd("extended-list", "list-value", 30.minutes)

        timeSource += 6.minutes

        assertNull(persistence["default"])
        assertEquals("extended-value", persistence["extended"])
        assertEquals(1, persistence.listSize("extended-list"))

        timeSource += 24.minutes

        assertNull(persistence["extended"])
        assertEquals(0, persistence.listSize("extended-list"))
        assertFalse("extended" in persistence)
    }

    @Test
    fun `scans prune multiple expired entries safely`() {
        val timeSource = TestTimeSource()
        val persistence = InMemoryPersistence<String>("test", 1.minutes, timeSource)
        persistence["one"] = "one"
        persistence["two"] = "two"
        timeSource += 2.minutes

        assertEquals(emptySet(), persistence.listAllKeys())
        assertEquals(emptyList(), persistence.getAll().toList())
    }
}
