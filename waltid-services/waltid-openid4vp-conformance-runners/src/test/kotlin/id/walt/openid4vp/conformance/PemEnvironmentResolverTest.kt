package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.resolvePemFromEnvironment
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PemEnvironmentResolverTest {

    @Test
    fun inlinePemTakesPrecedenceOverPemFile() {
        val pem = resolvePemFromEnvironment(
            inlinePemEnvironmentVariable = "INLINE_PEM",
            pemFileEnvironmentVariable = "PEM_FILE",
            environment = { name ->
                when (name) {
                    "INLINE_PEM" -> "inline certificate"
                    "PEM_FILE" -> "/does/not/exist.pem"
                    else -> null
                }
            },
        )

        assertEquals("inline certificate", pem)
    }

    @Test
    fun readsPemFromConfiguredFile() {
        val pemFile = Files.createTempFile("issuer-conformance", ".pem")
        try {
            Files.writeString(pemFile, "certificate from file")

            val pem = resolvePemFromEnvironment(
                inlinePemEnvironmentVariable = "INLINE_PEM",
                pemFileEnvironmentVariable = "PEM_FILE",
                environment = { name -> if (name == "PEM_FILE") pemFile.toString() else null },
            )

            assertEquals("certificate from file", pem)
        } finally {
            Files.deleteIfExists(pemFile)
        }
    }

    @Test
    fun failsClearlyForMissingPemFile() {
        val exception = assertFailsWith<IllegalArgumentException> {
            resolvePemFromEnvironment(
                inlinePemEnvironmentVariable = "INLINE_PEM",
                pemFileEnvironmentVariable = "PEM_FILE",
                environment = { name -> if (name == "PEM_FILE") "/does/not/exist.pem" else null },
            )
        }

        assertEquals(
            "Cannot find PEM file configured by PEM_FILE: /does/not/exist.pem",
            exception.message,
        )
    }
}
