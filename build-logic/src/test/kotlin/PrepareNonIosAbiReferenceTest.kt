import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrepareNonIosAbiReferenceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `projection removes only iOS declarations and preserves shared and other target APIs`() {
        val projected = withoutIosTargets(reference)
        assertTrue("// Targets: [js, linuxX64]" in projected)
        assertTrue("example/Shared" in projected)
        assertTrue("example/JsOnly" in projected)
        assertTrue("example/LinuxOnly" in projected)
        assertFalse("example/IosOnly" in projected)
        assertFalse("// Alias: ios" in projected)
        assertEquals(projected, withoutIosTargets(projected))
    }

    @Test
    fun `preparation preserves canonical and JVM files and removes stale output`() {
        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build()
        val source = project.file("api").apply { mkdirs() }
        val canonical = source.resolve("example.klib.api").apply { writeText(reference) }
        val jvm = source.resolve("jvm/example.api").apply {
            parentFile.mkdirs()
            writeText("public final class example/Shared {\n}\n")
        }
        val output = project.file("build/reference").apply { mkdirs() }
        output.resolve("stale.api").writeText("obsolete")
        val task = project.tasks.register("prepareReference", PrepareNonIosAbiReference::class.java).get()
        task.referenceDirectory.set(source)
        task.outputDirectory.set(output)

        task.prepare()

        assertEquals(reference, canonical.readText())
        assertEquals(jvm.readText(), output.resolve("jvm/example.api").readText())
        assertEquals(withoutIosTargets(reference), output.resolve("example.klib.api").readText())
        assertFalse(output.resolve("stale.api").exists())
    }

    private val reference = """
        // Klib ABI Dump
        // Targets: [iosArm64, iosSimulatorArm64, js, linuxX64]
        // Alias: ios => [iosArm64, iosSimulatorArm64]
        // Rendering settings:
        // - Signature version: 2
        // - Show manifest properties: true
        // - Show declarations: true

        // Library unique name: <example>
        final class example/Shared {
        }

        // Targets: [ios]
        final class example/IosOnly {
        }

        // Targets: [js]
        final class example/JsOnly {
        }

        // Targets: [linuxX64]
        final class example/LinuxOnly {
        }
    """.trimIndent() + "\n"
}
