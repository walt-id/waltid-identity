package id.walt.oid4vc.util

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

actual val http: HttpClient
  get() = HttpClient(Apache) {
    install(ContentNegotiation) {
      json()
    }
  }