package id.walt.cli.commands

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.testing.test
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import id.walt.cli.util.getResourcePath
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.StringWriter
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

        // openssl genrsa -out src/jvmTest/resources/rsa_by_openssl_pvt_key.pem 3072
        // openssl rsa -in src/jvmTest/resources/rsa_by_openssl_pvt_key.pem -pubout -out src/jvmTest/resources/rsa_by_openssl_pub_key.pem
        val inputFileName = "key/rsa_by_openssl_pvt_key.pem"
        val outputFileName = "key/rsa_by_openssl_pvt_key.jwk"

        val inputFilePath = getResourcePath(this, inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        KeyConvertCmd().parse(listOf("--input=${inputFilePath}"))

        // If the execution reaches this point, it means the command above didn't throw any exception
        assertTrue(true)

        deleteOutputFile(outputFilePath)
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
        val inputFileName = "key/invalidKey.jwk"
        var inputFilePath = getResourcePath(this, inputFileName)

        var result = KeyConvertCmd().test("--input=\"$inputFilePath\" --verbose")
        var expectedErrorMessage = ".*Invalid file format*".toRegex()
        assertContains(result.stderr, expectedErrorMessage)

        result = KeyConvertCmd().test("--input=\"$inputFilePath\" --verbose")
        expectedErrorMessage = ".*Missing key type \"kty\" parameter*".toRegex()
        assertContains(result.stderr, expectedErrorMessage)
    }

    @Test
    fun `should prompt for overwrite confirmation when the output file already exists`() {
        // Stored in src/jvmTest/resources
        val inputFileName = "key/rsa_by_openssl_pub_key.pem"
        val outputFileName = "existingFile.jwk"

        val inputFilePath = getResourcePath(this, inputFileName)
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
        val inputFileName = "key/ed25519_by_waltid_pvt_key.jwk"
        val outputFileName = "key/ed25519_by_waltid_pvt_key.pem"

        val inputFilePath = getResourcePath(this, inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        // Only as long as Ed25519 is not fully supported in JWKKey.exportPEM()
        val result1 = KeyConvertCmd().test("--input=\"$inputFilePath\"")
        assertContains(result1.stderr, "Something went wrong when converting the key")

        val result2 = KeyConvertCmd().test("--input=\"$inputFilePath\" --verbose")
        assertContains(result2.stderr, "Ed25519 keys cannot be exported as PEM yet")

        deleteOutputFile(outputFilePath)
    }

    @Test
    fun `should convert a PEM file to JWK`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "key/rsa_by_openssl_pub_key.pem"
        val outputFileName = "rsa_by_openssl_pub_key.jwk"

        val inputFilePath = getResourcePath(this, inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        deleteOutputFile(outputFilePath)

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\"")
        val expectedOutput = """.*Done. Converted "$inputFilePath" to "$outputFilePath".*""".toRegex()

        // Assert successful logging message
        assertContains(result.stdout, expectedOutput)

        deleteOutputFile(outputFilePath)
    }

    @Test
    fun `should convert RSA public key PEM file to a valid JWK`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "key/rsa_by_openssl_pub_key.pem"
        val outputFileName = "rsa_by_openssl_pub_key.jwk"

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

        testSuccessfulConvertion(inputFileName, outputFileName, expectedJWKFragments)
    }

    @Test
    fun `should convert RSA private key PEM file to a valid JWK`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "key/rsa_by_openssl_pvt_key.pem"
        val outputFileName = "rsa_by_openssl_pvt_key.jwk"

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

        testSuccessfulConvertion(inputFileName, outputFileName, expectedJWKFragments)
    }

    @Test
    @Ignore
    fun `should ask for the passphrase if input PEM file is encrypted and no passphrase is provided`() = runTest {
        val inputFileName = "key/rsa_encrypted_private_key.pem"
        val outputFileName = "key/rsa_encrypted_private_key.jwk"

        val inputFilePath = getResourcePath(this, inputFileName)

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
    }

    @Test
    fun `should NOT ask for the passphrase if input PEM file is encrypted and --passphrase is provided`() {

        // openssl genrsa -aes256 -passout pass:123123 -out rsa_by_openssl_encrypted_pvt_key.pem 2048
        val inputFileName = "key/rsa_by_openssl_encrypted_pvt_key.pem"
        val outputFileName = "rsa_by_openssl_encrypted_pvt_key.jwk"

        val inputFilePath = getResourcePath(this, inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\" --passphrase=123123")

        val expectedOutput = """.*Done. Converted "$inputFilePath" to "$outputFilePath".*""".toRegex()

        // Assert successful logging message
        assertContains(result.stdout, expectedOutput)

        deleteOutputFile(outputFilePath)
    }

    @Test
    fun `should convert RSA PEM public key extracted from an encrypted private key`() {

        // openssl rsa -in rsa_by_openssl_encrypted_pvt_key.pem -passin pass:123123 -pubout -out rsa_by_openssl_encrypted_pub_key.pem
        val inputFileName = "key/rsa_by_openssl_encrypted_pub_key.pem"
        val outputFileName = "rsa_by_openssl_encrypted_pub_key.jwk"

        val inputFilePath = getResourcePath(this, inputFileName)
        val outputFilePath = getOutputFilePath(inputFilePath, outputFileName)

        val result = KeyConvertCmd().test("--input=\"$inputFilePath\"")

        val expectedOutput = """.*Done. Converted "$inputFilePath" to "$outputFilePath".*""".toRegex()

        // Assert successful logging message
        assertContains(result.stdout, expectedOutput)

        deleteOutputFile(outputFilePath)

    }

    @Test
    fun `should convert encrypted RSA private key PEM file to a valid JWK`() {

        // Stored in src/jvmTest/resources
        val inputFileName = "key/rsa_by_openssl_encrypted_pvt_key.pem"
        val outputFileName = "rsa_by_openssl_encrypted_pvt_key.jwk"

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

        testSuccessfulConvertion(inputFileName, outputFileName, expectedJWKFragments, extraArgs)
    }

    @Test
    // @ValueSource(strings = {"ed25519_pub_key.pem", "ed25519_pvt_key.pem"})  --> JUnit dependency :-(
    fun `should fail when trying to convert Ed25519 PEM file`() {

        // ssh-keygen -t ed25519  -f ed25519_pvt_key_by_openssh.pem
        testFailingConversion("key/ed25519_by_openssh_pvt_key.pem", "Invalid file format", false)
        testFailingConversion("key/ed25519_by_openssh_pvt_key.pem", "unrecognised object: OPENSSH PRIVATE KEY", true)

        // openssl genpkey -algorithm ed25519 -out ed25519_pvt_key_by_openssl.pem
        testFailingConversion("key/ed25519_by_openssl_pvt_key.pem", "Invalid file format", false)
        testFailingConversion(
            "key/ed25519_by_openssl_pvt_key.pem",
            "Missing PEM-encoded public key to construct JWK",
            true
        )

        // openssl pkey -pubout -in src/jvmTest/resources/ed25519_pvt_key_by_openssl.pem -out src/jvmTest/resources/ed25519_pub_key_by_openssl.pem
        testFailingConversion("key/ed25519_by_openssl_pub_key.pem", "Invalid file format", false)
        testFailingConversion(
            "key/ed25519_by_openssl_pub_key.pem",
            "Unsupported algorithm of PEM-encoded key: EdDSA",
            true
        )
    }

    @Test
    @Ignore
    fun `should convert Ed25519 OpenSSL PEM file`() = Unit

    @Test
    @Ignore
    fun `should convert Ed25519 OpenSSH PEM file`() = Unit

    @Test
    fun `should convert secp256k1 PEM file with public and private key inside to JWK`() {

        // openssl ecparam -genkey -name secp256k1 -out src/jvmTest/resources/secp256k1_by_openssl_pub_pvt_key.pem
        val inputFileName = "key/secp256k1_by_openssl_pub_pvt_key.pem"
        val outputFileName = "secp256k1_by_openssl_pub_pvt_key.jwk"

        // Assert output file content
        val expectedJWKFragments = listOf(
            """"kty":"EC"""",
            """"crv":"secp256k1"""",
            """"x":".*"""",
            """"y":".*""""
        )

        testSuccessfulConvertion(inputFileName, outputFileName, expectedJWKFragments, "")
    }

    @Test
    @Ignore
    fun `should convert secp256k1 PEM file only with public key inside`() {

        // Doesn't work yet because BouncyCastle doesn't supoprt PEM object "BEGIN EC PUBLIC KEY"

        //  openssl pkey -in secp256k1_by_openssl_pvt_key.pem -pubout -out secp256k1_by_openssl_pub_key.pem
        val inputFileName = "key/secp256k1_by_openssl_pub_key.pem"
        val outputFileName = "key/secp256k1_by_openssl_pub_key.jwk"

        // Assert output file content
        val expectedJWKFragments = listOf(
            """"kty":"EC"""",
            """"crv":"secp256k1"""",
            """"x":".*"""",
            """"y":".*""""
        )

        testSuccessfulConvertion(inputFileName, outputFileName, expectedJWKFragments, "")
    }

    @Test
    fun `should fail when trying to convert secp256k1 PEM file only with private key inside`() {

        // ./waltid-cli.sh key generate -tsecp256k1 --output=src/jvmTest/resources/secp256k1_by_waltid_pvt_key.jwk
        // ./waltid-cli.sh key convert  --input=src/jvmTest/resources/secp256k1_by_waltid_pvt_key.jwk
        testFailingConversion(
            "key/secp256k1_by_waltid_pvt_key.pem",
            "incorrect format in file",
            false,
        )

        testFailingConversion(
            "key/secp256k1_by_waltid_pvt_key.pem",
            """the return value of "org.bouncycastle.openssl.PEMKeyPair.getPublicKeyInfo()" is null""",
            true,
        )

        // openssl storeutl -keys src/jvmTest/resources/secp256k1_by_openssl_pub_pvt_key.pem > src/jvmTest/resources/secp256k1_by_openssl_pvt_key.pem
        testFailingConversion(
            "key/secp256k1_by_openssl_pvt_key.pem",
            "Invalid file format",
            false,
        )

        testFailingConversion(
            "key/secp256k1_by_openssl_pvt_key.pem",
            "Missing PEM-encoded public key to construct JWK",
            true,
        )
    }

    @Test
    @Ignore
    fun `should convert Ed25519 public key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert Ed25519 private key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert Ed25519 pub & pvt key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert secp256k1 public key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert secp256k1 private key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert secp256k1 pub & pvt key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert secp256r1 public key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert secp256r1 private key PEM file to a valid JWK`() = Unit

    @Test
    @Ignore
    fun `should convert secp256r1 pub & pvt key PEM file to a valid JWK`() = Unit

    fun getOutputFilePath(inputFilePath: String, outputFileName: String): String {
        return "${inputFilePath.dropLastWhile { it != '/' }}$outputFileName"
    }

    fun deleteOutputFile(outputFilePath: String) {
        // TODO: Need to check if exists?
        File(outputFilePath).delete()
    }

    private fun testSuccessfulConvertion(
        inputFileName: String, outputFileName: String, expectedFragments: List<String>, extraArgs: String = ""
    ) {
        val inputFilePath = getResourcePath(this, inputFileName)
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

        deleteOutputFile(outputFilePath)
    }

    fun testFailingConversion(inputFileName: String, expectedErrorMessage: String, verbose: Boolean = false) {

        val inputFilePath = getResourcePath(this, inputFileName)
        var args = "--input=\"$inputFilePath\""

        if (verbose) {
            args = "$args --verbose"
        }

        val result = KeyConvertCmd().test(args)
        assertContains(result.stderr, expectedErrorMessage)
    }

}
