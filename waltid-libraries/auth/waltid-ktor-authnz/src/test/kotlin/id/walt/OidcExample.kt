package id.walt

import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.minutes

class
OidcExample {

    //@Test
    fun testJwt() = runTest(timeout = 20.minutes) {
        val server = startExample(
            wait = true, jwt = false
        )

        //implicit1Test()
        //explicit2Test()

        server.stop()
    }

}
