package id.walt.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.testing.test
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import id.walt.cli.commands.KeyConvertCmd
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.StringWriter
import java.net.URI
import java.text.ParseException
import kotlin.test.*

class WaltIdKeyConvertCmdTest {

    fun checkHelpMessage(command: KeyConvertCmd, args: List<String>) {
        assertFailsWith<PrintHelpMessage> {
            command.parse(args)
        }

        val result = command.test(args)
        val reHelpMsg = "Convert key files".toRegex()
        val reOpt1 = "-i, --input".toRegex()
        val reOpt2 = "-o, --output".toRegex()

        assertContains(result.stdout, reHelpMsg)
        assertContains(result.stdout, reOpt1)
        assertContains(result.stdout, reOpt2)
    }

    @Test
    fun `should print a help message`() {
        checkHelpMessage(KeyConvertCmd(), listOf("--help"))
    }

    // class WaltIdKeyConvertCmdStdOutTest {
    @Test
    fun `should print help message when called with no parameter`() = runTest {
        val command = KeyConvertCmd()
        val result = command.test(emptyList())
        val expected = "Convert key files.*".toRegex()
        assertContains(result.output, expected)
    }

    @Test
    fun `should fail if --input is provided with no value`() {
        val command = KeyConvertCmd()

        val failure = assertFailsWith<MultiUsageError> {
            command.parse(listOf("-i"))
        }

        assertTrue(failure.errors.any { it is IncorrectOptionValueCount })
        assertTrue(failure.errors.any { it is MissingOption })
    }

    @Test
    fun `should fail if --ouput is provided with no value`() {
        val command = KeyConvertCmd()

        val failure = assertFailsWith<MultiUsageError> {
            command.parse(listOf("-o"))
        }

        assertTrue(failure.errors.any { it is IncorrectOptionValueCount })
        assertTrue(failure.errors.any { it is MissingOption })
    }

    @Test
    fun `should NOT fail if --output is not provided`() {

        val inputFileName = "rsa_public_key.pem"
        var inputFilePath = getFilePath(inputFileName)

        KeyConvertCmd().parse(listOf("--input=${inputFilePath}"))

        // If the execution reaches this point, it means the command above didn't throw any exception
        assertTrue(true)
    }

    @Test
    fun `should fail if a non-existent input file is provided`() {
        val command = KeyConvertCmd()

        val inputFilename = "foo.jwk"
        val result = command.test(listOf("-i$inputFilename"))

        val expected = """.*file "$inputFilename" does not exist.*""".toRegex(RegexOption.IGNORE_CASE)
        assertContains(result.stderr, expected)
    }

    @Test
    fun `should fail with invalid input file`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "invalidKey.jwk"
        var inputFilePath = getFilePath(inputFileName)

        val failure = assertFailsWith<ParseException> {
            KeyConvertCmd().test("--input=\"$inputFilePath\"")
        }

        val expectedErrorMessage = ".*Missing key type \"kty\" parameter*".toRegex()
        assertContains(failure.message!!.toString(), expectedErrorMessage)
    }

    @Test
    fun `should prompt for overwrite confirmation when the output file already exists`() {
        // Stored in src/jvmTest/resources
        val inputFileName = "rsa_public_key.pem"
        val outputFileName = "existingFile.jwk"

        val inputFilePath = getFilePath(inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        // Creates the output file to simulate its previous existence
        File(outputFilePath).createNewFile()

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\" --output=\"$outputFilePath\"")
        val expectedOutput = """.*The file "$outputFilePath" already exists.*""".toRegex()

        // Assert successful logging message
        assertContains(result.stdout, expectedOutput)
    }

    @Test
    fun `should convert JWT input file to PEM`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "ed25519_valid_key.jwk"
        val outputFileName = "ed25519_valid_key.pem"

        val inputFilePath = getFilePath(inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        // val result = KeyConvertCmd().test("--input=\"$inputFilePath\"")
        // val expectedOutput = ".*Converting $inputFilePath to $outputFilePath.*".toRegex()
        // assertContains(result.stdout, expectedOutput)

        // Only as long as Ed25519 is not fully supported in LocalKey.exportPEM()
        val failure = assertFailsWith<NotImplementedError> {
            KeyConvertCmd().test("--input=\"$inputFilePath\"")
        }
    }

    @Test
    fun `should convert a PEM file to JWK`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "rsa_public_key.pem"
        val outputFileName = "rsa_public_key.jwk"

        val inputFilePath = getFilePath(inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        deleteOutputFile(outputFilePath)

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\"")
        val expectedOutput = """.*Done. Converted "$inputFilePath" to "$outputFilePath".*""".toRegex()

        // Assert successful logging message
        assertContains(result.stdout, expectedOutput)
    }

    @Test
    fun `should convert RSA public key PEM file to a valid JWK`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "rsa_public_key.pem"
        val outputFileName = "rsa_public_key.jwk"

        // Assert output file content
        val expectedJWKFragments = listOf(
            """"kty":"RSA"""",
            """("kid":".*")*""",      // Key id
            """"n":".*"""",           // Modulus parameter
            """"e":"AQAB"""",         // Exponent parameter
            // "\"p\":\".*\"",        // First prime factor
            // "\"q\":\".*\"",        // Second prime factor
            // "\"d\":\".*\"",        // Private expoent
            // "\"qi\":\".*\"",       // First CRT coefficient
            // "\"dp\":\".*\"",       // First factor CRT expoent
            // "\"dq\":\".*\"",       // Secobnd factor CRT expoent
        )

        testPEMConvertion(inputFileName, outputFileName, expectedJWKFragments)
    }

    @Test
    fun `should convert RSA private key PEM file to a valid JWK`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "rsa_private_key.pem"
        val outputFileName = "rsa_private_key.jwk"

        // Assert output file content
        val expectedJWKFragments = listOf(
            """"kty":"RSA"""",     // Key type
            """("kid":".*")*""",   // Key id
            """"n":".*"""",        // Modulus parameter
            """"e":".*"""",        // Exponent parameter
            """"p":".*"""",        // First prime factor
            """"q":".*"""",        // Second prime factor
            """"d":".*"""",        // Private expoent
            """"qi":".*"""",       // First CRT coefficient
            """"dp":".*"""",       // First factor CRT exponent
            """"dq":".*"""",       // Second factor CRT exponent
        )

        testPEMConvertion(inputFileName, outputFileName, expectedJWKFragments)
    }

    @Test
    @Ignore
    fun `should ask for the passphrase if input PEM file is encrypted and no passphrase is provided`() = runTest {
        val inputFileName = "rsa_encrypted_private_key.pem"
        val outputFileName = "rsa_encrypted_private_key.jwk"

        val inputFilePath = getFilePath(inputFileName)

        class PassphraseTerminal : TerminalInterface {
            override val info: TerminalInfo
                get() = TerminalInfo(
                    width = 0,
                    height = 0,
                    ansiLevel = AnsiLevel.NONE,
                    ansiHyperLinks = false,
                    outputInteractive = false,
                    inputInteractive = false,
                    crClearsLine = false
                )

            override fun completePrintRequest(request: PrintRequest) {
                StringWriter().write(request.text)
            }

            override fun readLineOrNull(hideInput: Boolean): String {
                return "123123"
            }

            suspend fun answerPrompt(input: String) = Channel<String>().send(input)

        }

        val result = KeyConvertCmd().context { terminal = Terminal(terminalInterface = PassphraseTerminal()) }
            .test("--input=\"${inputFilePath}\"")

        println(result)

        //
        // KeyConvertCmd().testkit("--input", inputFilePath) {
        //     expectOutput() // Reading key ...
        //     assertContains(expectOutput(), ".*Key encrypted. Please, inform the passphrase to decipher it.*".toRegex())
        //     provideInput("123123")
        //     // expectOutput() // Converting key
        //     // assertContains(expectOutput(), ".*Converted Key .JWK.*".toRegex())
        //     // ignoreOutputs()
        // }
    }

    @Test
    fun `should NOT ask for the passphrase if input PEM file is encrypted and --passphrase is provided`() {
        val inputFileName = "rsa_encrypted_private_key.pem"
        val outputFileName = "rsa_encrypted_private_key.jwk"

        val inputFilePath = getFilePath(inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\" --passphrase=123123")

        val expectedOutput = """.*Done. Converted "$inputFilePath" to "$outputFilePath".*""".toRegex()

        // Assert successful logging message
        assertContains(result.stdout, expectedOutput)
    }

    @Test
    fun `should convert encrypted RSA private key PEM file to a valid JWK`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "rsa_encrypted_private_key.pem"
        val outputFileName = "rsa_encrypted_private_key.jwk"

        // Assert output file content
        val expectedJWKFragments = listOf(
            """"kty":"RSA"""",     // Key type
            """("kid":".*")*""",   // Key id
            """"n":".*"""",        // Modulus parameter
            """"e":".*"""",        // Exponent parameter
            """"p":".*"""",        // First prime factor
            """"q":".*"""",        // Second prime factor
            """"d":".*"""",        // Private expoent
            """"qi":".*"""",       // First CRT coefficient
            """"dp":".*"""",       // First factor CRT exponent
            """"dq":".*"""",       // Second factor CRT exponent
        )

        val extraArgs = "--passphrase=123123"

        testPEMConvertion(inputFileName, outputFileName, expectedJWKFragments, extraArgs)
    }

    @Test
    @Ignore
    fun `should convert encrypted Ed25519 public key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert encrypted Ed25519 private key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert encrypted Ed25519 pub & pvt key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert encrypted secp256k1 public key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert encrypted secp256k1 private key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert encrypted secp256k1 pub & pvt key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert encrypted secp256r1 public key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert encrypted secp256r1 private key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert encrypted secp256r1 pub & pvt key PEM file to a valid JWK`() = Unit

    fun getFilePath(filename: String): String {
        // The returned URL has white spaces replaced by %20.
        // So, we need to decode it first to get rid of %20 from the file path
        return URI(this.javaClass.getClassLoader().getResource(filename)!!.toString()).path
    }

    fun getOutputFilePath(inputFilePath: String, outputFileName: String): String {
        return "${inputFilePath.dropLastWhile { it != '/' }}$outputFileName"
    }

    fun deleteOutputFile(outputFilePath: String) {
        // TODO: Need to check if exists?
        File(outputFilePath).delete()
    }

    private fun testPEMConvertion(
        inputFileName: String,
        outputFileName: String,
        expectedFragments: List<String>,
        extraArgs: String = ""
    ) {
        val inputFilePath = getFilePath(inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        deleteOutputFile(outputFilePath)
        val result = KeyConvertCmd().test("--input=\"$inputFilePath\" $extraArgs")

        val expectedOutput = """.*Done. Converted "$inputFilePath" to "$outputFilePath".*""".toRegex()

        // Assert successful logging message
        assertContains(result.stdout, expectedOutput)

        val convertedContent = File(outputFilePath).readText()

        // Assert the converted file structure
        expectedFragments.forEach {
            assertContains(convertedContent, it.toRegex())
        }
    }
}
