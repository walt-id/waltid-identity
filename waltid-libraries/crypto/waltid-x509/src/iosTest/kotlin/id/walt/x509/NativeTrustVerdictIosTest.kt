package id.walt.x509

import kotlin.test.Test
import kotlin.test.assertFailsWith

class NativeTrustVerdictIosTest {

    @Test
    fun nativeEvaluationRejectsLeafOutsidePermittedDnsSubtree() {
        val root = CertificateDer.fromPEMEncodedString(ROOT_PEM)
        val intermediate = CertificateDer.fromPEMEncodedString(INTERMEDIATE_PEM)
        val leaf = CertificateDer.fromPEMEncodedString(LEAF_PEM)

        assertFailsWith<X509ValidationException> {
            validateCertificateChain(
                leaf = leaf,
                chain = listOf(intermediate),
                trustAnchors = listOf(root),
                enableTrustedChainRoot = false,
                enableSystemTrustAnchors = false,
                enableRevocation = false,
            )
        }

        // The common fallback does not enforce name constraints. Native evaluation must not
        // fall through to this path after Apple has already rejected the chain.
        validateCertificateChainWithExplicitTrust(
            leaf = leaf,
            chain = listOf(intermediate),
            trustAnchors = listOf(root),
            enableTrustedChainRoot = false,
        )
    }

    private companion object {
        // Synthetic chain valid until 2036-09-15. Intermediate permits DNS:example.com
        // (non-critical, so the common explicit-chain verifier ignores it); leaf SAN is
        // attacker.example, which Apple's SecTrustEvaluateWithError rejects.
        private val ROOT_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIBgTCCASigAwIBAgIUWSCdHa43S/xoqjmwu17PVDUtYHgwCgYIKoZIzj0EAwIw
            HzEdMBsGA1UEAwwUV0FMLTg5NiBUZXN0IFJvb3QgQ0EwHhcNMjYwOTE4MDkyMzUw
            WhcNMzYwOTE1MDkyMzUwWjAfMR0wGwYDVQQDDBRXQUwtODk2IFRlc3QgUm9vdCBD
            QTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABAT8BovcOe6VAQRy/cdtAqRH5Pdi
            vwTGcEg/zjtimxUSKCzrBeTIDtKkifd5vZnTw4yF5zvFk70KVzjLuotxvaijQjBA
            MA8GA1UdEwEB/wQFMAMBAf8wDgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBR98X9o
            SEwa+h9zAY1edlYn1XxLyTAKBggqhkjOPQQDAgNHADBEAiBn+SQoBVMeZ+eHDm+f
            dD4uQOp173Bx2VaqCFioeUNE2wIgb5hD6uja8j7YiiRyUyXL5RwwQyS8IDUzCvmr
            GNmpLik=
            -----END CERTIFICATE-----
        """.trimIndent()

        private val INTERMEDIATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIB0TCCAXagAwIBAgIUeK6sxITzsofkhaqPAXnUNumNuuUwCgYIKoZIzj0EAwIw
            HzEdMBsGA1UEAwwUV0FMLTg5NiBUZXN0IFJvb3QgQ0EwHhcNMjYwOTE4MDkyMzUw
            WhcNMzYwOTE1MDkyMzUwWjArMSkwJwYDVQQDDCBXQUwtODk2IENvbnN0cmFpbmVk
            IEludGVybWVkaWF0ZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABAtP6gsSkW4p
            wwLKs1YgKAIpSDaWUNfjdjR3d1rk9EtWe6dWGrqxydjSKISbYaHrRECiCYdKUbnl
            wPeGucO/xC6jgYMwgYAwEgYDVR0TAQH/BAgwBgEB/wIBADAOBgNVHQ8BAf8EBAMC
            AQYwHQYDVR0OBBYEFCi/x9xEE71Xayv5/VzhFrqAajLLMB8GA1UdIwQYMBaAFH3x
            f2hITBr6H3MBjV52VifVfEvJMBoGA1UdHgQTMBGgDzANggtleGFtcGxlLmNvbTAK
            BggqhkjOPQQDAgNJADBGAiEAoepUwIXqMVvqjoAiVhI6yDPkSR/tN79UygGnN5JQ
            2m0CIQDgup7NwZCFZ97EERpIZsSN3DwRSyCsJouVP//Vomsk1g==
            -----END CERTIFICATE-----
        """.trimIndent()

        private val LEAF_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIB3TCCAYKgAwIBAgIUI1KjJIKVbhViSCpC1pPo09DsNHAwCgYIKoZIzj0EAwIw
            KzEpMCcGA1UEAwwgV0FMLTg5NiBDb25zdHJhaW5lZCBJbnRlcm1lZGlhdGUwHhcN
            MjYwOTE4MDkyMzUwWhcNMzYwOTE1MDkyMzUwWjAbMRkwFwYDVQQDDBBhdHRhY2tl
            ci5leGFtcGxlMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8MSb1TGGVwz4+Evo
            LYnGBeH4+NeYzP6T4JA3U9BXadvLdbG50BC/86adZU98Ol53F9B40SVZh2DgZ4o4
            CDC4ZKOBkzCBkDAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIHgDATBgNVHSUE
            DDAKBggrBgEFBQcDAjAdBgNVHQ4EFgQUf/j0v+G60FA7Bu0NETbCueDxbx4wHwYD
            VR0jBBgwFoAUKL/H3EQTvVdrK/n9XOEWuoBqMsswGwYDVR0RBBQwEoIQYXR0YWNr
            ZXIuZXhhbXBsZTAKBggqhkjOPQQDAgNJADBGAiEA0sQY88Z4tlNKuTWGaWkhI+Gz
            zQeXbqLcdNhMA7t5MUICIQDQVi2wSI0eU18nLq4bKJhuP8T6doehqgj6PwhvBlky
            cQ==
            -----END CERTIFICATE-----
        """.trimIndent()
    }
}
