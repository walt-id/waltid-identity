package id.walt.openid4vp.verifier

import id.walt.openid4vp.verifier.utils.UrlUtils
import id.walt.openid4vp.verifier.utils.UrlUtils.OriginUrlAttributes
import kotlin.test.Test
import kotlin.test.assertEquals

class DcApiOriginTest {

    @Test
    fun testDcApiOriginChecks() {
        @Suppress("HttpUrlsUsage")
        val testCases = mapOf(
            "https://example.org" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = true),
            "https://example.org/" to OriginUrlAttributes(nonComplexTrailingSlash = true, secureContext = true),
            "https://verifier.example.org" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = true),
            "http://example.org" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = false),
            "https://a.b.c.d.e.f.g.h.i.example.org" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = true),
            "http://a.b.c.d.e.f.g.h.i.example.org" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = false),
            "https://verifier2.portal.test.waltid.cloud" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = true),
            "https://verifier2.portal.test.waltid.cloud/" to OriginUrlAttributes(nonComplexTrailingSlash = true, secureContext = true),
            "https://xyz.localhost" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = true),
            "http://localhost:1234" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = true),
            "http://localhost" to OriginUrlAttributes(nonComplexTrailingSlash = false, secureContext = true),
            "http://localhost/" to OriginUrlAttributes(nonComplexTrailingSlash = true, secureContext = true),
            "http://localhost:1234/" to OriginUrlAttributes(nonComplexTrailingSlash = true, secureContext = true),
        )

        testCases.forEach { (url, attributes) ->
            println("Expecting $url to be $attributes")
            assertEquals(attributes, UrlUtils.checkDcApiOriginUrl(url))
        }
    }

}
