import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

val Project.identityCatalog: VersionCatalog
    get() = extensions.getByType(VersionCatalogsExtension::class.java).named("identityLibs")

val Project.javaLibraryVersion: Int
    get() = identityCatalog.findVersion("java-library").get().requiredVersion.toInt()

val Project.javaServiceVersion: Int
    get() = identityCatalog.findVersion("java-service").get().requiredVersion.toInt()

fun Project.getBooleanProperty(name: String): Boolean =
    providers.gradleProperty(name).orNull.toBoolean()

val Project.enableAndroidBuild: Boolean
    get() = getBooleanProperty("enableAndroidBuild")

val Project.enableIosBuild: Boolean
    get() = getBooleanProperty("enableIosBuild")
