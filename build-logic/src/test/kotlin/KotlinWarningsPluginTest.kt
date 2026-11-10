import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class KotlinWarningsPluginTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing opt-in fails production compilation`() = checkCompilation("main", "compileKotlin")

    @Test
    fun `missing opt-in fails test compilation`() = checkCompilation("test", "compileTestKotlin")

    private fun checkCompilation(sourceSet: String, task: String) {
        val root = temporaryFolder.newFolder()
        root.resolve("settings.gradle.kts").writeText("rootProject.name = \"warning-policy-fixture\"\n")
        root.resolve("build.gradle.kts").writeText("""
            plugins {
                id("org.jetbrains.kotlin.jvm")
                id("waltid.kotlin-warnings")
            }
            repositories { mavenCentral() }
            kotlin.compilerOptions.freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
        """.trimIndent())
        root.resolve("src/main/kotlin/Api.kt").apply {
            parentFile.mkdirs()
            writeText("""
                @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
                annotation class ExperimentalApi

                @ExperimentalApi
                fun experimentalApi() = Unit
            """.trimIndent())
        }
        val consumer = root.resolve("src/$sourceSet/kotlin/Consumer.kt").apply {
            parentFile.mkdirs()
            writeText("fun consumer() = experimentalApi()\n")
        }
        val runner = GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(task, "--console=plain", "--configuration-cache", "-Pkotlin.compiler.execution.strategy=in-process", "-Dorg.gradle.jvmargs=-Xmx1g")

        val failed = runner.buildAndFail()
        assertEquals(TaskOutcome.FAILED, failed.task(":$task")?.outcome, failed.output)
        assertTrue("opt-in" in failed.output, failed.output)
        assertTrue("[OPT_IN_USAGE]" in failed.output, failed.output)

        consumer.writeText("@OptIn(ExperimentalApi::class)\nfun consumer() = experimentalApi()\n")
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":$task")?.outcome)
    }
}
