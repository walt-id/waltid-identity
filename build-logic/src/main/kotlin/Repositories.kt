import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.kotlin.dsl.maven

fun RepositoryHandler.waltidRepositories(
    configureBeforeWaltid: RepositoryHandler.() -> Unit = {},
    configureReleases: MavenArtifactRepository.() -> Unit = {},
    configureSnapshots: MavenArtifactRepository.() -> Unit = {}
) {
    mavenLocal()
    mavenCentral()
    configureBeforeWaltid()
    maven("https://maven.waltid.dev/releases") {
        content {
            includeGroup("id.walt")
            includeGroup("org.cose")
        }
        configureReleases()
    }
    maven("https://maven.waltid.dev/snapshots") {
        content {
            includeGroup("id.walt")
            includeGroup("org.cose")
        }
        configureSnapshots()
    }
}
