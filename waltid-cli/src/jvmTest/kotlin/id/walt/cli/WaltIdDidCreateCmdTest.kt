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
    fun `should create a did-key when called with no argument`() {
        val result = command.test(emptyList<String>())

        assertContains(result.stdout, "DID created")
    }

    @Test
    fun `should create a valid did-key when called with no argument`() {
        val result = command.test(emptyList<String>())

        assertContains(result.stdout, "did:key")
    }

    @Test
    @Ignore
    fun `should have --type option`() {
    }

    @Test
    @Ignore
    fun `should have --key option`() {
    }

    @Test
    @Ignore
    fun `should generate a new key if one is not provided via the --key option`() {
    }

    @Test
    @Ignore
    fun `should have --domain option if --type=web`() {
    }

    @Test
    @Ignore
    fun `should have --path option if --type=web`() {
    }

    @Test
    @Ignore
    fun `should have --version option if --type=ebsi`() {
    }

    @Test
    @Ignore
    fun `should have --bearerToken option if --type=ebsi`() {
    }

    @Test
    @Ignore
    fun `should have --network option if --type=cheqd`() {
    }

    // @Test
    // fun `should have --xx option if --type=iota`() {}

    @Test
    @Ignore
    fun `should fail if the key file specified in the --key option does not exist`() {
    }

    @Test
    @Ignore
    fun `should create a valid did-jwk when called with --xxx=jwk`() {

    }

    @Test
    @Ignore
    fun `should create a valid did-web when called with --xxx=web`() {

    }

    @Test
    @Ignore
    fun `should create a valid did-ebsi when called with --xxx=ebsi`() {

    }

    @Test
    @Ignore
    fun `should create a valid did-cheqd when called with --xxx=cheqd`() {

    }

    @Test
    @Ignore
    fun `should create a valid did-iota when called with --xxx=iota`() {

    }
}