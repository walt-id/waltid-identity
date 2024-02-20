package id.walt.cli

import com.github.ajalt.clikt.core.IncorrectOptionValueCount
import com.github.ajalt.clikt.core.MissingOption
import com.github.ajalt.clikt.core.MultiUsageError
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import id.walt.cli.commands.KeyConvertCmd
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.URI
import kotlin.test.*
import com.wolpl.clikttestkit.test as testkit

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

        val expected = ".*$inputFilename not found.*".toRegex(RegexOption.IGNORE_CASE)
        assertContains(result.stderr, expected)
    }

    @Test
    fun `should fail with invalid input file`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "invalidKey.jwk"
        var inputFilePath = getFilePath(inputFileName)

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\"")
        val expectedOutput = ".*incorrect format in file $inputFilePath.*".toRegex(RegexOption.IGNORE_CASE)

        assertContains(result.stderr, expectedOutput)
    }

    @Test
    fun `should convert JWT input file to PEM`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "validKey.jwk"
        val outputFileName = "validKey.pem"

        val inputFilePath = getFilePath(inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        // val result = KeyConvertCmd().test("--input=\"$inputFilePath\"")
        // val expectedOutput = ".*Converting $inputFilePath to $outputFilePath.*".toRegex()
        // assertContains(result.stdout, expectedOutput)

        // Only as long as LocalKey.exportPEM() is not implemented.
        assertFails {
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

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\"")
        val expectedOutput = ".*$inputFilePath file converted to $outputFilePath.*".toRegex()

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
    fun `should ask for the passphrase if input PEM file is encrypted and no passphrase is provided`() = runTest {
        val inputFileName = "rsa_encrypted_private_key.pem"
        val outputFileName = "rsa_encrypted_private_key.jwk"

        val inputFilePath = getFilePath(inputFileName)

        KeyConvertCmd().testkit("--input", inputFilePath) {
            expectOutput()
            provideInput("123123")
            expectOutput() // ".*file converted to.*" --> I opened a ticket asking for Regex support
        }
    }

    @Test
    fun `should NOT ask for the passphrase if input PEM file is encrypted and --passphrase is provided`() {
        val inputFileName = "rsa_encrypted_private_key.pem"
        val outputFileName = "rsa_encrypted_private_key.jwk"

        val inputFilePath = getFilePath(inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\" --passphrase=123123")

        val expectedOutput = ".*$inputFilePath file converted to $outputFilePath.*".toRegex()

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
    fun `should convert encrypted secp256r1 pub & pvt key PEM file to a valid JWK`() {
    }

    fun getFilePath(filename: String): String {
        // The returned URL has white spaces replaced by %20.
        // So, we need to decode it first to get rid of %20 from the file path
        return URI(this.javaClass.getClassLoader().getResource(filename)!!.toString()).path
    }

    fun getOutputFilePath(inputFilePath: String, outputFileName: String): String {
        return "${inputFilePath.dropLastWhile { it != '/' }}$outputFileName"
    }

    private fun testPEMConvertion(
        inputFileName: String,
        outputFileName: String,
        expectedFragments: List<String>,
        extraArgs: String = ""
    ) {
        val inputFilePath = getFilePath(inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\" $extraArgs")

        val expectedOutput = ".*$inputFilePath file converted to $outputFilePath.*".toRegex()

        // Assert successful logging message
        assertContains(result.stdout, expectedOutput)

        val convertedContent = File(outputFilePath).readText()

        // Assert the converted file structure
        expectedFragments.forEach {
            assertContains(convertedContent, it.toRegex())
        }
    }
}