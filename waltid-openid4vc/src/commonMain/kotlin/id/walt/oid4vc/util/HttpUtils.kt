package id.walt.oid4vc.util

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

val http = HttpClient {
    install(ContentNegotiation) {
        json()
    }
}
