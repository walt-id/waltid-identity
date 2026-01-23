plugins {
    id("com.vanniktech.maven.publish")
    // `maven-publish`
}

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
            connection.set("scm:git:git://github.com/walt-id/waltid-identity.git")
            developerConnection.set("scm:git:ssh://github.com/walt-id/waltid-identity.git")
        }
    }
}
