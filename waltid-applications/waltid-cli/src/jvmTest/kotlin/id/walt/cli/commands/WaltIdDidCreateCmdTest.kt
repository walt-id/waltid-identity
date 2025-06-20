package id.walt.cli.commands

import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.testing.test
import id.walt.cli.util.getResourcePath
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.test.*

class WaltIdDidCreateCmdTest {

    val command = DidCreateCmd()

    @Test
    fun `should print help message when called with --help argument`() {
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should create a did-key from a random Ed25519 key when called with no argument`() {
        val result = command.test(emptyList())

        assertContains(result.stdout, "DID created")
    }

    @Test
    fun `should create a valid did-key when called with no argument`() {
        val result = command.test(emptyList())

        assertContains(result.stdout, "did:key:z[a-km-zA-HJ-NP-Z1-9]+".toRegex())
    }

    @Test
    fun `should have --method option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--method")
    }

    @Test
    fun `should have --key option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--key")
    }

    @Test
    fun `should have -j (useJwkJcsPub) option flag`() {
        val result = command.test("-h")

        assertContains(result.stdout, "-j, --useJwkJcsPub")
    }

    // --method

    @Test
    @Ignore
    fun `should not accept --method value other than key, jwk, web, ebsi, cheqd or iota`() {
        // Could be a parametrized test :-/
        val acceptedMethods = listOf("key", "jwk", "web", "ebsi", "cheqd", "iota")
        for (m in acceptedMethods) {
            assertDoesNotThrow {
                command.parse(listOf("--method=${m}"))
            }
        }

        val failure1 = assertFails { command.parse(listOf("--method=foo")) }
        assertContains(failure1.toString(), "BadParameterValue: invalid choice")

        val failure2 = assertFails { command.parse(listOf("--method=bar")) }
        assertContains(failure2.toString(), "BadParameterValue: invalid choice")
    }

    @Test
    @Ignore
    fun `should accept --method=key DID method`() {
    }

    @Test
    @Ignore
    fun `should accept --method=jwk DID method`() {
    }

    @Test
    @Ignore
    fun `should accept --method=web DID method`() {
    }

    @Test
    @Ignore
    fun `should accept --method=ebsi DID method`() {
    }

    @Test
    @Ignore
    fun `should accept --method=cheqd DID method`() {
    }

    @Test
    @Ignore
    fun `should accept --method=iota DID method`() {
    }

    @Test
    fun `should generate a new key if one is not provided via the --key option`() {
        val result = command.test("--method=key")

        val keyFormat = listOf(
            """"kty": "OKP"""",
            """"d": ".*?"""",
            """"crv": "Ed25519"""",
            """"kid": ".*?"""",
            """"x": ".*?""""
        )

        assertContains(result.stdout, "Key not provided. Let's generate a new one...")
        assertContains(result.stdout, "Key generated with thumbprint")
        keyFormat.forEach {
            assertContains(result.stdout, it.toRegex())
        }
    }

// KEY

    @Test
    fun `should fail if the key file specified in the --key option does not exist`() {
        assertFails {
            // FileNotFoundException
            command.parse(listOf("--key=./foo.bar"))
        }
    }

    @Test
    fun `should create different DIDs when provided with different keys`() {

        val didFile1 = "key/ed25519_key_sample1.json"
        val didFile2 = "key/ed25519_key_sample2.json"

        val didFilePath1 = getResourcePath(this, didFile1)
        val didFilePath2 = getResourcePath(this, didFile2)

        val result1 = command.test("-k \"${didFilePath1}\"")
        val result2 = command.test("-k \"${didFilePath2}\"")

        val regex = "did:key:z[a-km-zA-HJ-NP-Z1-9]+".toRegex()

        val did1 = regex.find(result1.stdout)?.groupValues?.get(0)
        val did2 = regex.find(result2.stdout)?.groupValues?.get(0)

        assertNotEquals(did1, did2)

    }

    @Test
    fun `should fail if a PEM key file is provided`() {
        val keyFile1 = "key/secp256k1_key_sample1.pem"
        val keyFilePath1 = getResourcePath(this, keyFile1)

        val failure = assertFailsWith<InvalidFileFormat> {
            command.parse(listOf("-k${keyFilePath1}"))
        }

        assertContains(failure.localizedMessage, "Invalid JSON")
    }

    @Test
    fun `should fail if the the provided --key is in a not supported format`() {
        val didFile1 = "key/invalidKey.jwk"
        val didFilePath1 = getResourcePath(this, didFile1)

        val failure = assertFailsWith<InvalidFileFormat> {
            command.parse(listOf("-k${didFilePath1}"))
        }

        assertContains(failure.localizedMessage, "Missing key type")
    }

    @Test
    fun `should succeed creating a DID key using jwk_jcs-pub with all key types`() {
        val keyFileList = listOf(
            getResourcePath(this, "key/ed25519_by_waltid_pvt_key.jwk"),
            getResourcePath(this, "key/rsa_by_waltid_pub_pvt_key.jwk"),
            getResourcePath(this, "key/secp256k1_by_waltid_pvt_key.jwk"),
            getResourcePath(this, "key/secp256r1_by_waltid_pub_pvt_key.jwk"),
        )
        for (keyFile in keyFileList) {
            val tempOutputFile = "${randomUUIDString()}.json"
            File(tempOutputFile).deleteOnExit()
            assertContains(command.test("-j -k \"$keyFile\" -o '$tempOutputFile'").output, "did:key:z[a-km-zA-HJ-NP-Z1-9]+".toRegex())
        }
    }


// JWK

    @Test
    @Ignore
    fun `should create a valid did-jwk when called with --method=jwk`() {

    }

// WEB

    @Test
    @Ignore
    fun `should have --domain option if --method=web`() {
    }

    @Test
    @Ignore
    fun `should have --path option if --method=web`() {
    }

    @Test
    @Ignore
    fun `should create a valid did-web when called with --method=web`() {

    }

// EBSI

    @Test
    @Ignore
    fun `should have --version option if --method=ebsi`() {
    }

    @Test
    @Ignore
    fun `should have --bearerToken option if --method=ebsi`() {
    }

    @Test
    @Ignore
    fun `should accept --version=1 if --method=ebsi`() {
    }

    @Test
    @Ignore
    fun `should accept --version=2 if --method=ebsi`() {
    }

    @Test
    @Ignore
    fun `should not accept --version value other than 1 or 2 if --method=ebsi`() {
    }

    @Test
    @Ignore
    fun `should create a valid did-ebsi when called with --method=ebsi`() {

    }

// CHEQD

    @Test
    @Ignore
    fun `should have --network option if --method=cheqd`() {
    }

    @Test
    @Ignore
    fun `should accept --network=mainnet option if --method=cheqd`() {
    }

    @Test
    @Ignore
    fun `should accept --network-testnet option if --method=cheqd`() {
    }

    @Test
    @Ignore
    fun `should not accept --network with value other than 'mainnet' and 'testnet' if --method=cheqd`() {
    }

    @Test
    @Ignore
    fun `should create a valid did-cheqd when called with --method=cheqd`() {

    }

// IOTA

    @Test
    @Ignore
    fun `should create a valid did-iota when called with --method=iota`() {

    }

// @Test
// fun `should have --xx option if --method=iota`() {}

    companion object {
        @JvmStatic
        @AfterAll
        fun tearDown() {
            Path(".").listDirectoryEntries("did%3Akey%3Az*").forEach { it.deleteIfExists() }
        }
    }

}
