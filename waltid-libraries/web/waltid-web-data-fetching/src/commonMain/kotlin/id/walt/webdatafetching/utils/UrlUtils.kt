package id.walt.webdatafetching.utils

import io.ktor.http.*

object UrlUtils {

    fun parseUrl(url: String): Url = try {
        Url(url)
    } catch (e: URLParserException) {
        throw IllegalArgumentException("Could not parse provided URL: $url", e)
    }

}
