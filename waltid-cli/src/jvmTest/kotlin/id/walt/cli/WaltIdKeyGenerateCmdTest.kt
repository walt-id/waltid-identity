package id.walt.cli

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.IncorrectOptionValueCount
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.testing.test
import id.walt.cli.commands.KeyGenerateCmd
import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WaltIdKeyGenerateCmdTest {

    private fun checkHelpMessage(command: KeyGenerateCmd, args: List<String>) {
        assertFailsWith<PrintHelpMessage> {
            command.parse(args)
        }

        val result = command.test(args)
        val reHelpMsg = "Generates a new cryptographic key".toRegex()
        val reOpt1 = "-t, --keyType".toRegex()

        assertContains(result.stdout, reHelpMsg)
        assertContains(result.stdout, reOpt1)
    }

    fun deleteGeneratedFile(output: String) {
        val generatedFilePath = Regex(".*Key saved at file (.*)").findAll(output).toList()[0].groupValues[1]
        File(generatedFilePath).delete()
    }

    @Test
    fun `should print a help message`() {
        checkHelpMessage(KeyGenerateCmd(), listOf("--help"))
    }

    @Test
    fun `should generate an Ed25519 key when no --keyType is provided`() = runTest {
        val command = KeyGenerateCmd()
        val result = command.test(emptyList())
        val expected = ".*Generating key of type ${KeyType.Ed25519.name}.*".toRegex()
        assertContains(result.output, expected)

        deleteGeneratedFile(result.output)
    }

    @Test
    fun `should fail if --keyType is provided with no value`() {
        val command = KeyGenerateCmd()

        assertFailsWith<IncorrectOptionValueCount> {
            command.parse(listOf("-t"))
        }

        assertFailsWith<IncorrectOptionValueCount> {
            command.parse(listOf("--keyType"))
        }
    }

    @Test
    fun `should fail if an invalid --keyType is provided`() {
        val command = KeyGenerateCmd()

        // val result = command.test(listOf("-t foo"))
        // --- result.output
        // Usage: generate [<options>]
        //
        // Error: invalid value for -t: invalid choice:  foo. (choose from ED25519, SECP256K1, SECP256R1, RSA)

        val failure = assertFailsWith<BadParameterValue> {
            command.parse(listOf("-t foo"))
        }

        val expected = "invalid choice".toRegex()
        failure.message?.let { assertContains(it, expected) }
    }

    @Test
    fun `should generate key of type Ed25519`() = runTest {
        val command = KeyGenerateCmd()

        val result = command.test("--keyType=Ed25519")
        val expected1 = ".*Generated Key.*".toRegex()
        val expected2 = "\"kty\":\"OKP\",\"d\":\".*?\",\"crv\":\"Ed25519\",\"kid\":\".*?\",\"x\":\".*?\"".toRegex()
        assertContains(result.stdout, expected1)
        assertContains(result.stdout, expected2)

        deleteGeneratedFile(result.output)
    }

    @Test
    fun `should generate key of type secp256k1`() = runTest {
        val command = KeyGenerateCmd()

        val result = command.test("--keyType=secp256k1")
        val expected1 = ".*Generated Key.*".toRegex()
        val expected2 =
            "\"kty\":\"EC\",\"d\":\".*?\",\"crv\":\"secp256k1\",\"kid\":\".*?\",\"x\":\".*?\",\"y\":\".*?\"}".toRegex()
        assertContains(result.stdout, expected1)
        assertContains(result.stdout, expected2)

        deleteGeneratedFile(result.output)
    }

    @Test
    fun `should ignore key type case`() = runTest {
        val command = KeyGenerateCmd()

        val expected1 = ".*Generated Key.*".toRegex()
        val expected2 =
            "\"kty\":\"EC\",\"d\":\".*?\",\"crv\":\"secp256k1\",\"kid\":\".*?\",\"x\":\".*?\",\"y\":\".*?\"}".toRegex()

        val result1 = command.test("--keyType=secp256k1")
        assertContains(result1.stdout, expected1)
        assertContains(result1.stdout, expected2)

        val result2 = command.test("--keyType=SECP256k1")
        assertContains(result2.stdout, expected1)
        assertContains(result2.stdout, expected2)

        val result3 = command.test("--keyType=SECP256K1")
        assertContains(result3.stdout, expected1)
        assertContains(result3.stdout, expected2)

        deleteGeneratedFile(result1.output)
        deleteGeneratedFile(result2.output)
        deleteGeneratedFile(result3.output)
    }

    @Test
    fun `should save generated key in file`() {
        val result = KeyGenerateCmd().test("--keyType=Ed25519")
        val regex = ".*Key saved at file (.*)".toRegex()
        assertContains(result.stdout, regex)

        val filePath = regex.find(result.stdout)!!.groupValues[1]
        assertTrue(File(filePath).exists())

        deleteGeneratedFile(result.output)
    }

    @Test
    fun `should generate file with a valid JWK`() {
        val result = KeyGenerateCmd().test("--keyType=Ed25519")

        val expectedAtStdOut = ".*Key saved at file (.*)".toRegex()
        assertContains(result.stdout, expectedAtStdOut)

        val filePath = expectedAtStdOut.find(result.stdout)!!.groupValues[1]
        val fileContent = File(filePath).readText()

        val validJWK = "\"kty\":\"OKP\",\"d\":\".*?\",\"crv\":\"Ed25519\",\"kid\":\".*?\",\"x\":\".*?\"".toRegex()
        assertContains(fileContent, validJWK)

        deleteGeneratedFile(result.output)
    }

    @Test
    fun `should override default file name`() {
        val outputFileName = "./myKey.json"

        val result = KeyGenerateCmd().test("--keyType=Ed25519 --output=${outputFileName}")
        val expectedAtStdOut = ".*Key saved at file ${outputFileName}".toRegex()

        assertContains(result.stdout, expectedAtStdOut)
        assertTrue(File(outputFileName).exists())

        deleteGeneratedFile(result.output)
    }
}