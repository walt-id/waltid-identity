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

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
