package id.walt.oid4vc.util

import id.walt.webdatafetching.WebDataFetchingConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*

val http = HttpClient {

    val log = KotlinLogging.logger("id.walt.oid4vc.http")

    engine {
        log.info { "Initializing oid4vc HTTP client with engine config: ${this::class.simpleName}" }
    }

    // For CI/CD OkHttp client should be used. CIO client seems to have some issues with timeouts when connecting to
    // localhost in CI/CD pipelines,
    // which can lead to hanging tests. OkHttp is more robust in these environments.
    WebDataFetchingConfiguration().applyConfigurationToHttpClient(this)
}
