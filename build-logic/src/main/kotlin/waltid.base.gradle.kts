import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.github.ben-manes.versions")
    //id("org.owasp.dependencycheck")
}

repositories {
    maven("https://maven.waltid.dev/releases")
    maven("https://maven.waltid.dev/snapshots")
    mavenCentral()
    google()
}

// The repo standardises on the mainline jdk18on Bouncy Castle (see libs.versions.toml). The lts8on line ships the
// same org.bouncycastle packages, so a transitive dependency pulling it in would duplicate every class and break
// the Android duplicate-class check. Keep it out everywhere rather than excluding per module.
configurations.configureEach {
    exclude(group = "org.bouncycastle", module = "bcprov-lts8on")
    exclude(group = "org.bouncycastle", module = "bcpkix-lts8on")
    exclude(group = "org.bouncycastle", module = "bcutil-lts8on")
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

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        listOf("-beta", "-alpha", "-rc").any { it in candidate.version.lowercase() } || candidate.version.takeLast(4).contains("RC")
    }
}
