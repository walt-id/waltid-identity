import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class X509PluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `plugin writes PEM certificates and JWK keys and stays up to date with the configuration cache`() {
        val root = fixture("""
            documentSigner("ds") {
                issuer = "iaca"
                subject = "C=AT,CN=Test Document Signer"
                crlDistributionPointUri = "https://iaca.example.com/crl"
            }
        """)
        val runner = runner(root)

        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":generateX509Certificates")?.outcome)
        val ds = root.resolve("build/generated/x509/ds")
        assertEquals(setOf("certificate.pem", "chain.pem", "private.jwk.json", "public.jwk.json"), ds.list()!!.toSet())
        assertEquals(1, Regex("BEGIN CERTIFICATE").findAll(ds.resolve("certificate.pem").readText()).count())
        assertEquals(2, Regex("BEGIN CERTIFICATE").findAll(ds.resolve("chain.pem").readText()).count())
        assertTrue("\"d\":" in ds.resolve("private.jwk.json").readText())
        assertTrue("\"d\":" !in ds.resolve("public.jwk.json").readText())

        val key = ds.resolve("private.jwk.json").readText()
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":generateX509Certificates")?.outcome)
        assertEquals(key, ds.resolve("private.jwk.json").readText())
    }

    @Test
    fun `each output file of a certificate can be configured`() {
        val root = fixture("""
            documentSigner("ds") {
                issuer = "iaca"
                subject = "C=AT,CN=Test Document Signer"
                crlDistributionPointUri = "https://iaca.example.com/crl"
                certificateFile = layout.projectDirectory.file("src/test/resources/mdoc/ds.pem")
                chainFile = layout.projectDirectory.file("src/test/resources/mdoc/ds-chain.pem")
                privateJwkFile = layout.projectDirectory.file("secrets/ds.jwk")
            }
        """)

        assertEquals(TaskOutcome.SUCCESS, runner(root).build().task(":generateX509Certificates")?.outcome)
        assertTrue(root.resolve("src/test/resources/mdoc/ds.pem").readText().startsWith("-----BEGIN CERTIFICATE-----"))
        assertTrue(root.resolve("src/test/resources/mdoc/ds-chain.pem").isFile)
        assertTrue("\"d\":" in root.resolve("secrets/ds.jwk").readText())
        // Unconfigured outputs keep their defaults, for this and every other certificate.
        assertEquals(setOf("public.jwk.json"), root.resolve("build/generated/x509/ds").list()!!.toSet())
        assertTrue(root.resolve("build/generated/x509/iaca/certificate.pem").isFile)
        assertFalse(root.resolve("build/generated/x509/ds/certificate.pem").exists())
    }

    @Test
    fun `certificates expiring within renewBeforeDays are regenerated`() {
        val root = fixture("""
            documentSigner("ds") {
                issuer = "iaca"
                subject = "C=AT,CN=Test Document Signer"
                crlDistributionPointUri = "https://iaca.example.com/crl"
                validityDays = 10
            }
        """)
        val runner = runner(root)

        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":generateX509Certificates")?.outcome)
        val key = root.resolve("build/generated/x509/ds/private.jwk.json").readText()
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":generateX509Certificates")?.outcome)
        assertTrue(key != root.resolve("build/generated/x509/ds/private.jwk.json").readText())

        root.resolve("build.gradle.kts").appendText("x509.renewBeforeDays = 5\n")
        assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":generateX509Certificates")?.outcome)
    }

    @Test
    fun `two outputs writing the same file are rejected`() {
        val root = fixture("""
            documentSigner("ds") {
                issuer = "iaca"
                subject = "C=AT,CN=Test Document Signer"
                crlDistributionPointUri = "https://iaca.example.com/crl"
                certificateFile = file("build/generated/x509/iaca/certificate.pem")
            }
        """)

        val result = runner(root).buildAndFail()
        assertTrue("are all configured to write" in result.output, result.output)
    }

    private fun fixture(documentSigner: String): File {
        val root = temporaryFolder.newFolder()
        root.resolve("settings.gradle.kts").writeText("""rootProject.name = "fixture"""")
        root.resolve("build.gradle.kts").writeText("""
            plugins {
                id("waltid.x509")
            }
            x509 {
                iaca("iaca") {
                    subject = "C=AT,CN=Test IACA"
                    issuerAlternativeNameUri = "https://iaca.example.com"
                }
        """.trimIndent() + "\n" + documentSigner.trimIndent().prependIndent("    ") + "\n}\n")
        return root
    }

    private fun runner(root: File) = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments("generateX509Certificates", "--configuration-cache", "--stacktrace")
}
