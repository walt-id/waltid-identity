package id.walt

import id.walt.authkit.plugins.configureRouting
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {
    // @Test
    fun testRoot() = testApplication {
        application {



            configureRouting()
        }


        client.post("/auth/userpass") {
            basicAuth("user1", "pass1")
        }
    }
}
