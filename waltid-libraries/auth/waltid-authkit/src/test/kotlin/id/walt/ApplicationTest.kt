package id.walt

import id.walt.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {



            configureRouting()
        }


        client.post("/auth/userpass") {
            basicAuth("user1", "pass1")
        }





        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Hello World!", bodyAsText())
        }
    }
}
