package id.walt.cli.commands

import com.github.ajalt.clikt.testing.test
import id.walt.cli.util.KeyUtil
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Crypto2KeyCommandTest {
    @Test
    fun `generate supports every advertised crypto2 signing target`() = runTest {
        val directory = Files.createTempDirectory("cli-key-generate")
        val cases = mapOf(
            "P-256" to "P-256",
            "P-384" to "P-384",
            "P-521" to "P-521",
            "Ed25519" to "Ed25519",
            "secp256k1" to "secp256k1",
            "RSA" to "RSA",
        )

        cases.forEach { (option, expected) ->
            val output = directory.resolve("$option.jwk")
            val result = KeyGenerateCmd().test(listOf("-t", option, "-o", output.toString()))
            assertEquals(0, result.statusCode, result.stderr)
            val jwk = Json.parseToJsonElement(output.readText()) as JsonObject
            assertEquals(expected, jwk["crv"]?.jsonPrimitive?.content ?: jwk["kty"]?.jsonPrimitive?.content)
            assertTrue("d" in jwk || "p" in jwk)
        }
    }

    @Test
    fun `private JWK PKCS8 and public SPKI round trip through strict PEM`() = runTest {
        val directory = Files.createTempDirectory("cli-key-convert")
        listOf("P-256", "P-384", "P-521", "Ed25519", "RSA", "secp256k1").forEach { algorithm ->
            val jwkPath = directory.resolve("$algorithm.jwk")
            assertEquals(0, KeyGenerateCmd().test(listOf("-t", algorithm, "-o", jwkPath.toString())).statusCode)
            val original = KeyUtil.importJwk(jwkPath.readText())
            val expectedThumbprint = KeyUtil.thumbprint(original)

            val privatePem = directory.resolve("$algorithm-private.pem")
            val privateRoundTrip = directory.resolve("$algorithm-private-roundtrip.jwk")
            assertEquals(0, KeyConvertCmd().test(listOf("-i", jwkPath.toString(), "-o", privatePem.toString())).statusCode)
            assertTrue(privatePem.readText().startsWith("-----BEGIN PRIVATE KEY-----\n"))
            assertEquals(0, KeyConvertCmd().test(listOf("-i", privatePem.toString(), "-o", privateRoundTrip.toString())).statusCode)
            assertEquals(expectedThumbprint, KeyUtil.thumbprint(KeyUtil.importJwk(privateRoundTrip.readText())))

            val publicPem = directory.resolve("$algorithm-public.pem")
            val publicRoundTrip = directory.resolve("$algorithm-public-roundtrip.jwk")
            assertEquals(
                0,
                KeyConvertCmd().test(
                    listOf("-i", jwkPath.toString(), "-f", "SPKI", "-o", publicPem.toString())
                ).statusCode,
            )
            assertTrue(publicPem.readText().startsWith("-----BEGIN PUBLIC KEY-----\n"))
            assertEquals(0, KeyConvertCmd().test(listOf("-i", publicPem.toString(), "-o", publicRoundTrip.toString())).statusCode)
            val publicJwk = Json.parseToJsonElement(publicRoundTrip.readText()) as JsonObject
            assertFalse("d" in publicJwk)
            assertFalse("p" in publicJwk)
            assertEquals(expectedThumbprint, Jwk.sha256Thumbprint(encoded(publicJwk)))
        }
    }

    @Test
    fun `conversion rejects malformed encrypted and algorithm-confused keys`() = runTest {
        val directory = Files.createTempDirectory("cli-key-invalid")
        val encrypted = directory.resolve("encrypted.pem")
        encrypted.writeText("-----BEGIN ENCRYPTED PRIVATE KEY-----\nMA==\n-----END ENCRYPTED PRIVATE KEY-----\n")
        val encryptedResult = KeyConvertCmd().test(listOf("-i", encrypted.toString()))
        assertTrue(encryptedResult.statusCode != 0)
        assertTrue(encryptedResult.stderr.contains("Encrypted PEM is not supported"))

        val keyPath = directory.resolve("p256.jwk")
        assertEquals(0, KeyGenerateCmd().test(listOf("-t", "P-256", "-o", keyPath.toString())).statusCode)
        val jwk = Json.parseToJsonElement(keyPath.readText()) as JsonObject
        keyPath.writeText(Json.encodeToString(JsonObject(jwk + ("alg" to JsonPrimitive("RS256")))))
        val confusedResult = KeyConvertCmd().test(listOf("-i", keyPath.toString(), "--verbose"))
        assertTrue(confusedResult.statusCode != 0)
        assertTrue(confusedResult.stderr.contains("incompatible"))
    }

    @Test
    fun `malformed JWK and strict PEM inputs fail clearly`() {
        val directory = Files.createTempDirectory("cli-key-malformed")
        val malformedInputs = mapOf(
            "array.jwk" to "[]",
            "missing-kty.jwk" to """{"crv":"P-256","x":"AQ","y":"AQ"}""",
            "bad-public.pem" to "-----BEGIN PUBLIC KEY-----\nM*==\n-----END PUBLIC KEY-----\n",
            "pkcs1.pem" to "-----BEGIN RSA PRIVATE KEY-----\nMA==\n-----END RSA PRIVATE KEY-----\n",
        )
        malformedInputs.forEach { (name, content) ->
            val input = directory.resolve(name).also { it.writeText(content) }
            val result = KeyConvertCmd().test(listOf("-i", input.toString(), "--verbose"))
            assertTrue(result.statusCode != 0, "$name unexpectedly succeeded")
            assertTrue(result.stderr.isNotBlank(), "$name produced no error")
        }
    }

    @Test
    fun `private material is hidden unless output or show-private is explicit`() {
        val directory = Files.createTempDirectory("cli-key-secrets")
        val keyPath = directory.resolve("private.jwk")
        val generated = KeyGenerateCmd().test(listOf("-t", "P-256", "-o", keyPath.toString()))
        assertEquals(0, generated.statusCode)
        assertFalse(generated.stdout.contains("\"d\""))
        assertTrue(keyPath.readText().contains("\"d\""))

        val metadataOnly = KeyGenerateCmd().test(listOf("-t", "Ed25519"))
        assertEquals(0, metadataOnly.statusCode)
        assertFalse(metadataOnly.stdout.contains("\"d\""))
        assertContains(metadataOnly.stdout, "Private key not written")

        val shown = KeyGenerateCmd().test(listOf("-t", "Ed25519", "--show-private"))
        assertEquals(0, shown.statusCode)
        assertContains(shown.stdout, "WARNING: displaying private key material")
        assertTrue(shown.stdout.contains("\"d\""))

        val implicitPrivateConversion = KeyConvertCmd().test(listOf("-i", keyPath.toString()))
        assertTrue(implicitPrivateConversion.statusCode != 0)
        assertContains(implicitPrivateConversion.stderr, "requires --output or explicit --show-private")

        val privatePem = directory.resolve("private.pem")
        val written = KeyConvertCmd().test(listOf("-i", keyPath.toString(), "-o", privatePem.toString()))
        assertEquals(0, written.statusCode)
        assertFalse(written.stdout.contains("BEGIN PRIVATE KEY"))
        assertTrue(privatePem.readText().startsWith("-----BEGIN PRIVATE KEY-----"))

        val shownConversion = KeyConvertCmd().test(listOf("-i", keyPath.toString(), "--show-private"))
        assertEquals(0, shownConversion.statusCode)
        assertContains(shownConversion.stdout, "BEGIN PRIVATE KEY")

        val help = KeyGenerateCmd().test("--help").stdout + KeyConvertCmd().test("--help").stdout
        assertContains(help, "WARNING")
        assertContains(help, "--show-private")
    }

    private fun encoded(jwk: JsonObject) = EncodedKey.Jwk(
        BinaryData(Json.encodeToString(jwk).encodeToByteArray()),
        privateMaterial = false,
    )
}
