import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Run the real policy and renderer mode reader without compiling the wallet or starting a simulator. */
class WalletVisualVerificationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val ciVariables = setOf("CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION")
    private val roborazziVersion = System.getProperty("walletRoborazziVersion")

    @Test
    fun `ordinary tests verify from either root and changed baselines invalidate cached tests`() {
        for (coordinated in listOf(false, true)) {
            val root = fixture(coordinated)
            val runner = runner(root, ci = true, ":wallet:test")
            assertEquals(TaskOutcome.SUCCESS, runner.build().task(":wallet:test")?.outcome)
            assertEquals(TaskOutcome.UP_TO_DATE, runner.build().task(":wallet:test")?.outcome)
            module(root).resolve("src/visualTest/snapshots/androidHostTest/reference.txt").writeText("changed reference")
            assertEquals(TaskOutcome.SUCCESS, runner.build().task(":wallet:test")?.outcome)
        }
    }

    @Test
    fun `CI rejects explicitly disabled verification`() {
        val result = runner(fixture(coordinated = true), ci = true,
            ":wallet:test", "-Proborazzi.test.verify=false").buildAndFail()
        assertTrue("CI must not disable wallet screenshot verification." in result.output)
    }

    @Test
    fun `CI rejects recording and compare-only properties and tasks`() {
        val root = fixture(coordinated = true)
        for (arguments in listOf(
            listOf(":wallet:test", "-Proborazzi.test.record=true"),
            listOf(":wallet:test", "-Proborazzi.test.compare=true"),
            listOf(":wallet:recordRoborazziJvm"),
            listOf(":wallet:verifyAndRecordRoborazziJvm"),
            listOf(":wallet:compareRoborazziJvm"),
        )) {
            val result = runner(root, ci = true, *arguments.toTypedArray()).buildAndFail()
            assertTrue("CI must verify reviewed wallet snapshots; it cannot record or accept baselines." in result.output)
        }
    }

    @Test
    fun `explicit local recording retains the renderer record mode`() {
        runner(fixture(coordinated = true), ci = false,
            ":wallet:recordRoborazziJvm", "-PexpectedMode=Record").build()
    }

    @Test
    fun `native tests receive the same default verification and baseline input`() {
        for (coordinated in listOf(false, true)) {
            runner(fixture(coordinated, native = true), ci = true, ":wallet:inspectNativeMode").build()
        }
    }

    private fun module(root: File) = root.resolve("waltid-identity/wallet")

    private fun fixture(coordinated: Boolean, native: Boolean = false): File {
        val root = temporaryFolder.newFolder()
        root.resolve("settings.gradle.kts").writeText("""
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
            rootProject.name = "wallet-policy-test"
            include(":wallet")
            project(":wallet").projectDir = file("waltid-identity/wallet")
        """.trimIndent())
        // Identity and unified roots deliberately have no build-level verification override.
        root.resolve("gradle.properties").writeText("org.gradle.parallel=false\n")
        root.resolve("waltid-identity").mkdirs()
        if (coordinated) root.resolve("waltid-identity/gradle.properties").writeText("roborazzi.test.verify=true\n")
        val module = module(root).apply { mkdirs() }
        module.resolve("src/visualTest/snapshots/androidHostTest").mkdirs()
        module.resolve("src/visualTest/snapshots/androidHostTest/reference.txt").writeText("reviewed reference")
        module.resolve("build.gradle.kts").writeText(if (native) nativeBuild else """
            plugins {
                id("org.jetbrains.kotlin.jvm")
                id("io.github.takahirom.roborazzi") version "$roborazziVersion"
                id("waltid.wallet.visual")
            }
            repositories { mavenCentral() }
            dependencies {
                testImplementation("junit:junit:4.13.2")
                testImplementation("io.github.takahirom.roborazzi:roborazzi-core-jvm:$roborazziVersion")
            }
            tasks.test {
                useJUnit()
                systemProperty("expectedMode", providers.gradleProperty("expectedMode").getOrElse("Verify"))
            }
        """.trimIndent())
        if (!native) {
            val source = module.resolve("src/test/java/RendererModeTest.java")
            source.parentFile.mkdirs()
            source.writeText("""
                import com.github.takahirom.roborazzi.RoborazziOptions;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import org.junit.Test;
                import static org.junit.Assert.assertEquals;

                public class RendererModeTest {
                    @Test public void rendererUsesRequestedMode() throws Exception {
                        String mode = new RoborazziOptions().getTaskType().name();
                        assertEquals(System.getProperty("expectedMode"), mode);
                        Path results = Path.of(System.getProperty("roborazzi.result.dir"));
                        Files.createDirectories(results);
                        Files.writeString(results.resolve("observed-mode.txt"), mode);
                    }
                }
            """.trimIndent())
        }
        return root
    }

    private fun runner(root: File, ci: Boolean, vararg arguments: String) = GradleRunner.create()
        .withProjectDir(root)
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace", "--console=plain")
        .withEnvironment(System.getenv().filterKeys { it !in ciVariables } + if (ci) mapOf("CI" to "true") else emptyMap())

    private val nativeBuild = """
        import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
        import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
        plugins {
            id("waltid.wallet.visual")
        }
        tasks.register<KotlinNativeSimulatorTest>("iosSimulatorArm64Test") {
            executableProperty.set(files("test.kexe"))
            device.set("fixture")
            standalone.set(true)
        }
        tasks.register("inspectNativeMode") {
            val nativeTest = tasks.named<KotlinNativeTest>("iosSimulatorArm64Test")
            doLast {
                check(nativeTest.get().environment["SIMCTL_CHILD_roborazzi.test.verify"] == "true")
                check(nativeTest.get().environment["SIMCTL_CHILD_roborazzi.output.dir"].toString()
                    .endsWith("src/visualTest/snapshots/iosSimulatorArm64"))
                check(nativeTest.get().inputs.properties["walletSnapshotVerification"] == "true")
            }
        }
    """.trimIndent()
}
