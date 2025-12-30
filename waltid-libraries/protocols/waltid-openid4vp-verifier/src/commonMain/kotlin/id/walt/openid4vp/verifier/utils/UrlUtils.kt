package id.walt.openid4vp.verifier.utils

import io.ktor.http.*

object UrlUtils {

    data class OriginUrlAttributes(
        /**
         * If URL is non-complex (e.g. https://verifier.example.org) but has a
         * trailing slash (e.g. https://verifier.example.org/). Only checked if
         * the URL is non-complex, as we assume that the user otherwise knows
         * what they are doing.
         * */
        val nonComplexTrailingSlash: Boolean,
        /** Is URL a "secure context" -> https or localhost */
        val secureContext: Boolean
    )

    fun checkDcApiOriginUrl(origin: String): OriginUrlAttributes {
        val url =
            runCatching { Url(origin) }.getOrElse { throw IllegalArgumentException("Provided Origin URL \"$origin\" is not a valid URL.") }

        val isComplex = url.user != null || url.password != null || url.fragment.isNotEmpty() || !url.parameters.isEmpty()
                || (url.encodedPath.isNotEmpty() && url.encodedPath != "/")
        val nonComplexTrailingSlash = !isComplex && origin.endsWith("/")

        val isLocalhost = url.host in listOf("localhost", "127.0.0.1", "::1") || url.host.endsWith(".localhost")
        val secureContext = isLocalhost || url.protocol == URLProtocol.HTTPS

        return OriginUrlAttributes(
            nonComplexTrailingSlash = nonComplexTrailingSlash,
            secureContext = secureContext
        )
    }

}
