import com.google.cloud.tools.jib.gradle.JibExtension

plugins {
    id("com.google.cloud.tools.jib") // Automatically applied by ktor, applying it here just so that IntelliJ knows what's going on
}

fun getDockerCredentials(rootDir: File): Pair<String, String> {
    val envUsername = providers.environmentVariable("DOCKER_USERNAME").getOrNull()
    val envPassword = providers.environmentVariable("DOCKER_PASSWORD").getOrNull()
    val usernameFile = File(rootDir, "secret-docker-username.txt")
    val passwordFile = File(rootDir, "secret-docker-password.txt")

    return Pair(
        envUsername ?: if (usernameFile.isFile) usernameFile.readLines().first() else "DOCKER_IS_UNAUTHENTICATED",
        envPassword ?: if (passwordFile.isFile) passwordFile.readLines().first() else "DOCKER_IS_UNAUTHENTICATED"
    )
}

configure<JibExtension> {
    val (user, pass) = getDockerCredentials(rootDir)

    container {
        workingDirectory = "/${project.name}"
    }
    from {
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
    to {
        image = project.name.replaceFirst("waltid-", "waltid/") // waltid-verifier-api2 -> waltid/verifier-api2
        tags = setOf("${project.version}", "latest")
        auth {
            username = user
            password = pass
        }
    }
}
