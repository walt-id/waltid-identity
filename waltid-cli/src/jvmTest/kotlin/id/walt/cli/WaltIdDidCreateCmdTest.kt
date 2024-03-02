package id.walt.cli

import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import id.walt.cli.commands.DidCreateCmd
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class WaltIdDidCreateCmdTest {

    val command = DidCreateCmd()

    // @Test
    // fun `should print help message when called with no arguments`() = runTest {
    //     assertFailsWith<PrintHelpMessage> {
    //         command.parse(emptyList())
    //     }
    //
    //     val result = command.test()
    //     assertContains(result.stdout, "Creates a new Decentralized Identity")
    // }

    @Test
    fun `should print help message when called with --help argument`() {
        assertFailsWith<PrintHelpMessage> {
            command.parse(listOf("--help"))
        }
    }

    @Test
    fun `should create a key of type did when called with no argument`() {
        val result = command.test(emptyList<String>())

        assertContains(result.stdout, "DID created")
    }

    @Test
    fun `should create a valid did-key when called with no argument`() {
        val result = command.test(emptyList<String>())

        assertContains(result.stdout, "did:key:z[a-km-zA-HJ-NP-Z1-9]+".toRegex())
    }

    @Test
    fun `should have --method option`() {
        val result = command.test(listOf("--help"))

        assertContains(result.stdout, "--method")
    }

    @Test
    @Ignore
    fun `should have --key option`() {
    }

    @Test
    @Ignore
    fun `should have --useJwkJcsPub option???? When?`() {

    }

    // --method

    @Test
    @Ignore
    fun `should not accept --method value other than key, jwk, web, ebsi, cheqd or iota`() {
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
    @Ignore
    fun `should generate a new key if one is not provided via the --key option`() {
    }

    // --key

    @Test
    @Ignore
    fun `should fail if the key file specified in the --key option does not exist`() {
    }

    @Test
    @Ignore
    fun `should fail if the key file specified in the --key option is in a not supported format`() {
    }

    // KEY

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


}