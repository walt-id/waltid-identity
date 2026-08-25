import org.gradle.plugins.signing.SigningExtension

plugins {
    id("com.vanniktech.maven.publish")
    // `maven-publish`
}

// Maven Central is an *additional* target. Publishing to maven.waltid.dev below is unchanged
// and stays enabled for every module applying this convention.
val isCentralArtifact = WaltidCentralPublishing.isCentralArtifact(project.path)

// Signing is enabled only when a GPG key is actually configured (normally in
// ~/.gradle/gradle.properties as `signing.gnupg.keyName`). This keeps ordinary development and
// CI snapshot publishing to maven.waltid.dev working on machines without a signing key, while
// Maven Central releases are signed. The release script fails closed if the key is missing.
val gpgKeyName = providers.gradleProperty("signing.gnupg.keyName")
val signingEnabled = isCentralArtifact && gpgKeyName.isPresent

publishing {
    repositories {
        maven {
            name = "Maven"
            val releasesRepoUrl = uri("https://maven.waltid.dev/releases")
            val snapshotsRepoUrl = uri("https://maven.waltid.dev/snapshots")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            credentials {
                val envUsername = providers.environmentVariable("MAVEN_USERNAME").getOrNull()
                val envPassword = providers.environmentVariable("MAVEN_PASSWORD").getOrNull()

                // Helper to read local file fallback
                fun readSecret(name: String): String =
                    File(rootDir, "secret_maven_$name.txt").let { if (it.isFile) it.readLines().first() else "" }

                username = envUsername ?: readSecret("username")
                password = envPassword ?: readSecret("password")
            }
        }
    }
}

mavenPublishing {

    if (isCentralArtifact) {
        // Manual review in https://central.sonatype.com/publishing/deployments is required.
        // Do not switch this to automatic release without an explicit decision.
        publishToMavenCentral(automaticRelease = false)
    }

    if (signingEnabled) {
        signAllPublications()
    }

    @Suppress("UnstableApiUsage")
    configureBasedOnAppliedPlugins()

    pom {
        url.set("https://walt.id")
        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0")
            }
        }
        developers {
            developer {
                id.set("walt.id")
                name.set("walt.id")
                email.set("office@walt.id")
                url.set("https://walt.id")
            }
        }
        scm {
            // Maven Central requires an SCM url in addition to the connection entries.
            url.set("https://github.com/walt-id/waltid-identity")
            connection.set("scm:git:https://github.com/walt-id/waltid-identity.git")
            developerConnection.set("scm:git:ssh://git@github.com/walt-id/waltid-identity.git")
        }
    }
}

// Use the developer's existing GnuPG keyring / gpg-agent rather than an exported key in the
// repository. The signing plugin is applied by the Maven publish plugin, so react to it.
if (signingEnabled) {
    plugins.withId("signing") {
        extensions.configure<SigningExtension>("signing") {
            useGpgCmd()
        }
    }
}
