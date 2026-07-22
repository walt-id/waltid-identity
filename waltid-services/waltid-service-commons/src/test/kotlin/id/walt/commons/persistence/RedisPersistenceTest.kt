package id.walt.commons.persistence

import kotlin.test.Test
import kotlin.test.assertEquals

class RedisPersistenceTest {
    @Test
    fun `cluster topology selects masters and ignores replicas`() {
        val topology = """
            id-a 10.0.0.1:6379@16379 master - 0 0 1 connected 0-8191
            id-b 10.0.0.2:6379@16379 slave id-a 0 0 2 connected
            id-c 10.0.0.3:6379@16379 myself,master - 0 0 3 connected 8192-16383
            id-d 10.0.0.4:6379@16379 slave,fail id-c 0 0 4 disconnected
        """.trimIndent()

        assertEquals(setOf("10.0.0.1:6379", "10.0.0.3:6379"), redisClusterMasterAddresses(topology))
    }
}
