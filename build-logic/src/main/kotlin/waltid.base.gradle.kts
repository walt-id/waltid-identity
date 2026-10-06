import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("io.github.ben-manes.versions")
    id("waltid.bouncycastle")
    //id("org.owasp.dependencycheck")
}

repositories {
    maven("https://maven.waltid.dev/releases")
    maven("https://maven.waltid.dev/snapshots")
    mavenCentral()
    google()
}

// Without this, a failing test prints only its exception class and source location. Kotlin/Native test tasks in
// particular leave no HTML report to inspect on a CI runner, so the message - e.g. the OSStatus behind an iOS
// keychain failure - was lost entirely.
tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// Live suites (OpenID conformance, e2e, integration) are one Gradle Test task wrapping
// many minutes of work. A global JUnit/task timeout kills those. Opt in from CI instead.
tasks.withType<Test>().configureEach {
    val junitTimeout = providers.environmentVariable("JUNIT_TIMEOUT_DEFAULT").orNull
    val longRunning = project.name in setOf(
        "waltid-openid4vp-conformance-runners",
        "waltid-e2e-tests",
        "waltid-integration-tests",
    )
    if (!junitTimeout.isNullOrBlank() && junitTimeout != "none" && !longRunning) {
        systemProperty("junit.jupiter.execution.timeout.default", junitTimeout)
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    checkConstraints = true
    // Cinterop commonization resolves derived artifacts, not dependency update candidates.
    filterConfigurations = Spec {
        it.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name != "kotlin-commonized-cinterop"
    }
    rejectPreReleases = true
}
