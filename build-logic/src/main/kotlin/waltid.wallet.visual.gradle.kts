import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

// Wallet snapshot policy belongs to the module, including when a coordinated build owns Gradle properties.
val verification = providers.gradleProperty("roborazzi.test.verify").orElse("true")
val visualCi = listOf("CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION")
    .any { providers.environmentVariable(it).isPresent }
val explicitSnapshotTask = gradle.startParameter.taskNames.any { it.substringAfterLast(':').let { name ->
    name.startsWith("recordRoborazzi") || name.startsWith("verifyRoborazzi") ||
        name.startsWith("verifyAndRecordRoborazzi") || name.startsWith("compareRoborazzi")
} }
if (visualCi) {
    check(listOf("record", "compare").none { providers.gradleProperty("roborazzi.test.$it").orNull == "true" } &&
        gradle.startParameter.taskNames.none { it.substringAfterLast(':').let { name ->
            name.startsWith("recordRoborazzi") || name.startsWith("verifyAndRecordRoborazzi") || name.startsWith("compareRoborazzi")
        } }) { "CI must verify reviewed wallet snapshots; it cannot record or accept baselines." }
    tasks.withType<KotlinNativeTest>().configureEach {
        if (!providers.environmentVariable("WALLET_VISUAL_RUN_ID").isPresent) {
            filter.excludeTestsMatching("id.walt.walletdemo.compose.ui.WalletVisualIosTest")
        }
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    val variant = when (this) {
        is Test -> "androidHostTest"
        is KotlinNativeTest -> name.removeSuffix("Test")
        else -> return@configureEach
    }
    if (!explicitSnapshotTask && !providers.gradleProperty("roborazzi.test.verify").isPresent &&
        listOf("record", "compare").none { providers.gradleProperty("roborazzi.test.$it").orNull == "true" }) {
        // Roborazzi reads build-level properties, so its ordinary-test defaults are absent in the unified build.
        val baselines = layout.projectDirectory.dir("src/visualTest/snapshots/$variant")
        val results = layout.buildDirectory.dir("test-results/roborazzi/$variant/results")
        val defaults = mapOf(
            "roborazzi.test.verify" to "true",
            "roborazzi.output.dir" to baselines.asFile.absolutePath,
            "roborazzi.compare.output.dir" to layout.buildDirectory.dir("outputs/visual-diffs/$variant").get().asFile.absolutePath,
            "roborazzi.result.dir" to results.get().asFile.absolutePath,
            "roborazzi.project.path" to layout.projectDirectory.asFile.absolutePath,
        )
        when (this) {
            is Test -> systemProperties(defaults)
            is KotlinNativeTest -> defaults.forEach { (key, value) -> environment("SIMCTL_CHILD_$key", value) }
        }
        inputs.property("walletSnapshotVerification", verification)
        inputs.files(baselines).withPropertyName("walletSnapshotBaselines").withPathSensitivity(PathSensitivity.RELATIVE)
        outputs.dir(results)
    }
    if (visualCi) {
        val snapshotVerification = verification
        onlyIf {
            check(snapshotVerification.get() == "true") { "CI must not disable wallet screenshot verification." }
            true
        }
    }
}
