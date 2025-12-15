import com.google.cloud.tools.jib.gradle.JibExtension
import io.ktor.plugin.features.DockerImageRegistry.Companion.dockerHub

plugins {
    id("io.ktor.plugin")

    id("com.google.cloud.tools.jib") // Automatically applied by ktor, applying it here just so that IntelliJ knows what's going on
}


fun getDockerCredentials(rootDir: File): Pair<String, String> {
    val envUsername = System.getenv("DOCKER_USERNAME")
    val envPassword = System.getenv("DOCKER_PASSWORD")
    val usernameFile = File(rootDir, "secret-docker-username.txt")
    val passwordFile = File(rootDir, "secret-docker-password.txt")

    return Pair(
        envUsername ?: if (usernameFile.isFile) usernameFile.readLines().first() else "",
        envPassword ?: if (passwordFile.isFile) passwordFile.readLines().first() else ""
    )
}

// 2. Configure Ktor Docker extension
ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_21)

        localImageName.set(project.name.replaceFirst("waltid-", "waltid/")) // waltid-verifier-api2 -> waltid/verifier-api2
        imageTag.set("${project.version}")

        val (user, pass) = getDockerCredentials(rootDir)

        externalRegistry.set(
            dockerHub(
                appName = provider { project.name.removePrefix("waltid-") },
                username = provider { user },
                password = provider { pass }
            )
        )
    }
}



configure<JibExtension> {
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
}
