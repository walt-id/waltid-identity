package id.walt.cli.commands

import com.github.ajalt.clikt.testing.test
import id.walt.cli.WaltIdCmd
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherAndHelpTest {
    @Test
    fun `root and command help expose retained command families`() {
        val root = WaltIdCmd().test("--help")
        assertEquals(0, root.statusCode)
        listOf("key", "did", "vc", "vp").forEach { assertContains(root.stdout, it) }

        assertContains(KeyGenerateCmd().test("--help").stdout, "--show-private")
        assertContains(KeyConvertCmd().test("--help").stdout, "--output-format")
        assertContains(DidCreateCmd().test("--help").stdout, "--method")
        assertContains(VCVerifyCmd().test("--help").stdout, "Signature is mandatory")
        assertContains(VPCreateCmd().test("--help").stdout, "--dcql-query")
        assertContains(VPVerifyCmd().test("--help").stdout, "--verifiable-presentation")
    }

    @Test
    fun `missing required options and malformed values fail parsing`() {
        assertEquals(0, KeyConvertCmd().test(emptyList()).statusCode)
        assertEquals(0, VCSignCmd().test(emptyList()).statusCode)
        assertEquals(0, VPCreateCmd().test(emptyList()).statusCode)
        assertEquals(0, VPVerifyCmd().test(emptyList()).statusCode)
        assertTrue(KeyConvertCmd().test(listOf("-i")).statusCode != 0)
        assertTrue(VCSignCmd().test(listOf("-k")).statusCode != 0)
        assertTrue(VPCreateCmd().test(listOf("-hd", "did:example:holder")).statusCode != 0)
        assertTrue(VPVerifyCmd().test(listOf("-n", "nonce")).statusCode != 0)
        assertTrue(KeyGenerateCmd().test(listOf("-t", "unknown")).statusCode != 0)
        assertTrue(DidCreateCmd().test(listOf("-m", "web")).statusCode != 0)
    }

    @Test
    fun `installed Linux and tracked launchers work from unrelated directories`() {
        val module = File(".").canonicalFile
        val installed = File(module, "build/install/waltid-jvm/bin/waltid")
        val temporary = Files.createTempDirectory("cli-launcher-cwd").toFile()
        listOf(module, temporary).forEach { workingDirectory ->
            val result = run(listOf(installed.absolutePath, "--help"), workingDirectory)
            assertEquals(0, result.exitCode, result.output)
            assertContains(result.output, "Commands:")
        }

        val tracked = run(listOf("sh", File(module, "waltid-cli.sh").absolutePath, "--help"), temporary)
        assertEquals(0, tracked.exitCode, tracked.output)
        assertContains(tracked.output, "Commands:")
    }

    @Test
    fun `Windows installed launcher uses bounded wildcard classpath`() {
        val script = File("build/install/waltid-jvm/bin/waltid.bat")
        assertTrue(script.isFile)
        val content = script.readText()
        assertContains(content, "%APP_HOME%\\lib\\*")
        assertContains(content, "id.walt.cli.MainKt")
        assertFalse(content.contains(".jar;"))
        assertTrue(content.lineSequence().maxOf(String::length) < 8191)

        val tracked = File("waltid-cli.bat").readText()
        assertContains(tracked, "%~dp0")
        assertContains(File("waltid-cli.sh").readText(), "SCRIPT_DIR")
        assertContains(File("waltid-cli-development.sh").readText(), "SCRIPT_DIR")
    }

    private fun run(command: List<String>, workingDirectory: File): ProcessResult {
        val process = ProcessBuilder(command)
            .directory(workingDirectory)
            .redirectErrorStream(true)
            .start()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Launcher timed out")
        return ProcessResult(process.exitValue(), process.inputStream.bufferedReader().readText())
    }

    private data class ProcessResult(val exitCode: Int, val output: String)
}
