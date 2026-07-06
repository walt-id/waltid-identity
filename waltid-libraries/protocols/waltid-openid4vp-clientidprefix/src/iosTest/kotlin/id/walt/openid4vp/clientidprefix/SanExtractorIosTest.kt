package id.walt.openid4vp.clientidprefix

import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import kotlin.test.Test
import kotlin.test.assertEquals

class SanExtractorIosTest {

    @Test
    fun extractsDnsSubjectAlternativeNamesFromCertificate() {
        val der =
            "MIIBVjCB/aADAgECAgg9JU9yqLTSlDAKBggqhkjOPQQDAjAfMR0wGwYDVQQDDBR2ZXJpZmllci5leGFtcGxlLmNvbTAeFw0yNTEwMTQwNTM2MTZaFw0yNjEwMTQwNTM2MTZaMB8xHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEy+ytwhEa31/os6dz9B5ZDLkJpnigeh2FEhpoRO/aPwB8WPKE6HkRQGlVq4Fuer7MA1WGgoZlsOYDQPw9vro61KMjMCEwHwYDVR0RBBgwFoIUdmVyaWZpZXIuZXhhbXBsZS5jb20wCgYIKoZIzj0EAwIDSAAwRQIhAJicm/egD5feJgUuca6FsbMIqUxP6baOALkrEKew1G34AiB0shCYfQtfSg1ks5SFo90669eACA6snm20HjRlHc2XPg=="
                .decodeFromBase64()

        assertEquals(
            expected = listOf("verifier.example.com"),
            actual = extractSanDnsNamesFromDer(der).getOrThrow(),
        )
    }
}
