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
        root.resolve("settings.gradle.kts").writeText("rootProject.name = \"warning-policy-fixture\"\ninclude(\":consumer\")\n")
        root.resolve("build.gradle.kts").writeText("""
            plugins {
                id("org.jetbrains.kotlin.jvm") apply false
                id("waltid.kotlin-warnings")
            }
        """.trimIndent())
        val module = root.resolve("consumer").apply { mkdirs() }
        module.resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.kotlin.jvm") }
            repositories { mavenCentral() }
            kotlin.compilerOptions.freeCompilerArgs.add("-Xrender-internal-diagnostic-names")
        """.trimIndent())
        module.resolve("src/main/kotlin/Api.kt").apply {
            parentFile.mkdirs()
            writeText("""
                @RequiresOptIn(level = RequiresOptIn.Level.WARNING)
                annotation class ExperimentalApi

                @ExperimentalApi
                fun experimentalApi() = Unit

                interface Api {
                    @ExperimentalApi
                    fun action()
                }
            """.trimIndent())
        }
        val consumer = module.resolve("src/$sourceSet/kotlin/Consumer.kt").apply {
            parentFile.mkdirs()
            writeText("class Consumer : Api { override fun action() = experimentalApi() }\n")
        }
        val runner = GradleRunner.create()
            .withProjectDir(root)
            .withPluginClasspath()
            .withArguments(":consumer:$task", "--console=plain", "--configuration-cache", "-Pkotlin.compiler.execution.strategy=in-process", "-Dorg.gradle.jvmargs=-Xmx1g")

        val failed = runner.buildAndFail()
        assertEquals(TaskOutcome.FAILED, failed.task(":consumer:$task")?.outcome, failed.output)
        for (diagnostic in listOf("OPT_IN_USAGE", "OPT_IN_OVERRIDE")) {
            assertTrue(failed.output.lineSequence().any { it.startsWith("e:") && "[$diagnostic]" in it }, failed.output)
        }

        consumer.writeText("@OptIn(ExperimentalApi::class)\nclass Consumer : Api { override fun action() = experimentalApi() }\n")
        assertEquals(TaskOutcome.SUCCESS, runner.build().task(":consumer:$task")?.outcome)
    }
}
